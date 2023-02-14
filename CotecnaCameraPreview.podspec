
  Pod::Spec.new do |s|
    s.name = 'CotecnaCameraPreview'
    s.version = '2.0.0'
    s.summary = 'Camera preview'
    s.license = 'MIT'
    s.homepage = 'https://github.com/Cotecna-Inspection/camera-preview.git'
    s.author = 'Ariel Hernandez Musa'
    s.source = { :git => 'https://github.com/Cotecna-Inspection/camera-preview.git', :tag => s.version.to_s }
    s.source_files = 'ios/Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
    s.ios.deployment_target  = '13.0'
    s.dependency 'Capacitor'
    s.swift_version = '5.1'
  end
