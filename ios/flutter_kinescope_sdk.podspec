Pod::Spec.new do |s|
  s.name             = 'flutter_kinescope_sdk'
  s.version          = '0.2.6'
  s.summary          = 'Flutter plugin for the Kinescope player.'
  s.description      = <<-DESC
Native iOS player backed by KinescopeSDK 1.0.3.
                       DESC
  s.homepage         = 'https://kinescope.io/'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Kinescope' => 'support@kinescope.io' }
  s.source           = { :path => '.' }
  s.source_files = 'flutter_kinescope_sdk/Sources/flutter_kinescope_sdk/**/*.swift'
  s.dependency 'Flutter'
  # 1.0.3 is published as a git tag, not a CocoaPods Trunk version.
  # The app Podfile must include:
  #   pod 'KinescopeSDK', :git => 'https://github.com/kinescope/ios-kinescope-player.git', :tag => '1.0.3'
  s.dependency 'KinescopeSDK'
  s.platform = :ios, '15.0'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
  s.swift_version = '5.0'
end
