require 'json'
require 'fileutils'
require 'open3'
require 'csv'
require 'optparse'
require 'tmpdir'

# This harness configures generated builds against compiled plugin classes from each checkout.
# Compilation is a prerequisite so it never enters the measured configuration interval.
options = {scenarios: {}, projects: 200, configurations: 37, warmups: 3, runs: 9}
parser = OptionParser.new do |args|
  args.banner = 'Usage: ruby benchmark.rb --scenario LABEL=CHECKOUT [--scenario ...] [options]'
  args.on('--scenario LABEL=CHECKOUT', 'Compare compiled plugin classes from this checkout; repeat for each variant') do |value|
    label, source = value.split('=', 2)
    abort 'Scenario must be LABEL=CHECKOUT with a simple label' unless label && label.match?(/\A[a-z0-9][a-z0-9_-]*\z/) && source && !source.empty?
    abort "Duplicate scenario: #{label}" if options[:scenarios].key?(label)
    options[:scenarios][label] = File.expand_path(source)
  end
  args.on('--work-dir PATH', 'Directory for generated projects, logs, profiles and results') { |value| options[:work_dir] = File.expand_path(value) }
  args.on('--projects N', Integer, 'Subproject count (default: 200)') { |value| options[:projects] = value }
  args.on('--configurations N', Integer, 'Eligible configurations per subproject (default: 37)') { |value| options[:configurations] = value }
  args.on('--warmups N', Integer, 'Warmup builds per variant (default: 3)') { |value| options[:warmups] = value }
  args.on('--runs N', Integer, 'Measured builds per variant (default: 9)') { |value| options[:runs] = value }
  args.on('--prepare-only', 'Generate the fixtures without running Gradle') { options[:prepare_only] = true }
  args.on('-h', '--help', 'Show this help') { puts args; exit }
end
parser.parse!
abort parser.to_s if options[:scenarios].empty? || !ARGV.empty?
abort 'Projects, configurations and measured runs must be positive; warmups must be nonnegative' unless
  options.values_at(:projects, :configurations, :runs).all?(&:positive?) && options[:warmups] >= 0

BASE = options[:work_dir] || Dir.mktmpdir('resolution-rules-configuration-')
RESULTS = File.join(BASE, 'results')
SCENARIOS = options[:scenarios].freeze
PROJECTS = options[:projects]
CONFIGURATIONS = options[:configurations]
WARMUPS = options[:warmups]
RUNS = options[:runs]
TIMER = File.expand_path('configuration-timer.init.gradle', __dir__)
abort "Measured results already exist in #{RESULTS}; choose a new work directory" if File.exist?(File.join(RESULTS, 'benchmark-runs.json'))
SCENARIOS.each_value do |source|
  abort "Missing Gradle wrapper in #{source}" unless File.file?(File.join(source, 'gradlew'))
  metadata = File.join(source, 'build/pluginUnderTestMetadata/plugin-under-test-metadata.properties')
  abort "Missing #{metadata}; run ./gradlew classes pluginUnderTestMetadata from that checkout first" unless File.file?(metadata)
end
FileUtils.mkdir_p(RESULTS)

def groovy_string(value)
  "'#{value.gsub('\\', '\\\\').gsub("'", "\\\\'")}'"
end

def prepare
  common = {reason: 'Configuration benchmark', author: 'benchmark', date: '2026-11-03'}
  rules = {
    replace: Array.new(285) { |i| common.merge(module: "benchmark.legacy:library#{i}", with: "benchmark.replacement:library#{i}") },
    substitute: Array.new(1049) { |i| common.merge(module: "benchmark.substitute:library#{i}", with: "benchmark.target:library#{i}:1.0") },
    reject: Array.new(692) { |i| common.merge(module: "benchmark.rejected:library#{i}:1.0") },
    deny: Array.new(22) { |i| common.merge(module: "benchmark.denied:library#{i}") },
    exclude: Array.new(41) { |i| common.merge(module: "benchmark.excluded:library#{i}") },
    align: Array.new(106) { |i| common.merge(group: "benchmark.aligned#{i}") }
  }
  dependency_jars = nil
  SCENARIOS.each do |scenario, source|
    fixture = File.join(BASE, "fixture-#{scenario}")
    FileUtils.mkdir_p(File.join(fixture, 'gradle'))
    FileUtils.cp(File.join(source, 'gradle/gradle-daemon-jvm.properties'), File.join(fixture, 'gradle/gradle-daemon-jvm.properties'))
    metadata = File.read(File.join(source, 'build/pluginUnderTestMetadata/plugin-under-test-metadata.properties'))
    classpath = metadata.lines.find { |line| line.start_with?('implementation-classpath=') }.split('=', 2).last.strip.split('\\:')
    jars = classpath.select { |path| path.end_with?('.jar') }.sort
    abort 'Variants resolved different dependency JARs; align their classpaths before comparing' if dependency_jars && jars != dependency_jars
    dependency_jars = jars
    names = Array.new(PROJECTS) { |i| "project#{i}" }
    names.each { |name| FileUtils.mkdir_p(File.join(fixture, name)) }
    File.write(File.join(fixture, 'settings.gradle'), "rootProject.name = 'configuration-comparison'\ninclude #{names.map { |name| groovy_string(name) }.join(', ')}\n")
    File.write(File.join(fixture, 'rules.json'), JSON.pretty_generate(rules))
    File.write(File.join(fixture, 'gradle.properties'), <<~PROPERTIES)
      org.gradle.configuration-cache=false
      org.gradle.parallel=false
      org.gradle.configureondemand=false
      org.gradle.workers.max=2
      org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
    PROPERTIES
    File.write(File.join(fixture, 'build.gradle'), <<~GRADLE)
      buildscript {
          dependencies { classpath files(#{classpath.map { |path| groovy_string(path) }.join(', ')}) }
      }
      apply plugin: 'com.netflix.nebula.resolution-rules'
      dependencies { resolutionRules files('rules.json') }
      subprojects {
          apply plugin: 'java'
          apply plugin: 'com.netflix.nebula.resolution-rules'
          dependencies {
              (0..<20).each { implementation "benchmark.library:library${it}:1.0" }
          }
          def eligible = configurations.count { !it.name.startsWith('resolutionRules') && it.name != 'archives' }
          def additional = #{CONFIGURATIONS} - eligible
          assert additional >= 0 : "Requested fewer configurations than the Java plugin already creates"
          (0..<additional).each { index ->
              configurations.create("benchmark${index}") { extendsFrom configurations.implementation }
          }
      }
      gradle.projectsEvaluated {
          def count = subprojects.sum { p -> p.configurations.count { !it.name.startsWith('resolutionRules') && it.name != 'archives' } }
          assert count == #{PROJECTS * CONFIGURATIONS}
          println "BENCHMARK projects=#{PROJECTS} configurations=${count} rules=#{rules.values.sum(&:length)}"
      }
    GRADLE
  end
  puts "Prepared identical workloads: #{PROJECTS} subprojects, #{CONFIGURATIONS} eligible configurations per project, #{rules.values.sum(&:length)} rules."
  puts "Artifacts: #{BASE}"
end

def duration_seconds(value)
  value.scan(/([0-9.]+)(ms|s|m|h)/).sum do |number, unit|
    number.to_f * {'ms' => 0.001, 's' => 1, 'm' => 60, 'h' => 3600}.fetch(unit)
  end
end

def run_one(scenario, kind, iteration)
  source = SCENARIOS.fetch(scenario)
  fixture = File.join(BASE, "fixture-#{scenario}")
  label = "#{scenario}-#{kind}-#{iteration}"
  log = File.join(RESULTS, "#{label}.log")
  command = ['./gradlew', '-p', fixture, 'help', '--profile', '--no-configuration-cache', '--no-parallel', '--offline', '--max-workers=2', '--console=plain', '-I', TIMER]
  started = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  output, status = Open3.capture2e(*command, chdir: source)
  File.write(log, output)
  raise "#{label} failed; see #{log}\n#{output.lines.last(30).join}" unless status.success?
  raise "#{label} did not configure expected projects" unless output.include?("BENCHMARK projects=#{PROJECTS} configurations=#{PROJECTS * CONFIGURATIONS}")
  profile = Dir.glob(File.join(fixture, 'build/reports/profile/profile-*.html')).max_by { |path| File.mtime(path) }
  html = File.read(profile)
  value = html.match(/<td>Configuring Projects<\/td>\s*<td[^>]*>([^<]+)<\/td>/)&.captures&.first
  raise "Configuration time missing from #{profile}" unless value
  milliseconds = output.match(/CONFIGURATION_PHASE_MS=([0-9.]+)/)&.captures&.first
  raise "Full configuration phase timing missing from #{log}" unless milliseconds
  seconds = milliseconds.to_f / 1000.0
  saved_profile = File.join(RESULTS, "#{label}.html")
  FileUtils.cp(profile, saved_profile)
  row = {scenario: scenario, kind: kind, iteration: iteration, configuration_seconds: seconds,
         profile_configuration_seconds: duration_seconds(value),
         elapsed_seconds: Process.clock_gettime(Process::CLOCK_MONOTONIC) - started, profile: saved_profile, log: log}
  puts "#{label}: full configuration=#{format('%.3f', seconds)}s (profile project counter=#{value})"
  STDOUT.flush
  row
end

def median(values)
  sorted = values.sort
  middle = sorted.length / 2
  sorted.length.odd? ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0
end

def run
  rows = []
  WARMUPS.times do |index|
    order = SCENARIOS.keys.rotate(index % SCENARIOS.length)
    order.each { |scenario| rows << run_one(scenario, 'warmup', index + 1) }
  end
  RUNS.times do |index|
    order = SCENARIOS.keys.rotate(index % SCENARIOS.length)
    order.each do |scenario|
      rows << run_one(scenario, 'measured', index + 1)
      File.write(File.join(RESULTS, 'benchmark-runs.json'), JSON.pretty_generate(rows))
    end
  end
  summary = SCENARIOS.keys.to_h do |scenario|
    values = rows.select { |row| row[:scenario] == scenario && row[:kind] == 'measured' }.map { |row| row[:configuration_seconds] }
    [scenario, {runs: values.length, median_seconds: median(values), min_seconds: values.min, max_seconds: values.max, values: values}]
  end
  File.write(File.join(RESULTS, 'benchmark-summary.json'), JSON.pretty_generate(summary))
  CSV.open(File.join(RESULTS, 'benchmark-runs.csv'), 'w') do |csv|
    csv << rows.first.keys
    rows.each { |row| csv << row.values }
  end
  puts JSON.pretty_generate(summary)
end

prepare
run unless options[:prepare_only]
