{
  description = "Stream Studio – Mobile-first live streaming application";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
  let
    system = "x86_64-linux";
    pkgs = import nixpkgs {
      inherit system;
      config.allowUnfree = true;
      config.android_sdk.accept_license = true;
    };
    androidSdk = pkgs.androidenv.composeAndroidPackages {
      buildToolsVersions = [ "36.0.0" "35.0.0" "34.0.0" ];
      platformVersions = [ "36" "35" "34" ];
      abiVersions = [ "x86_64" ];
      includeEmulator = true;
      includeSystemImages = true;
      systemImageTypes = [ "google_apis" ];
      includeNDK = false;
    };
  in {
    devShells.${system}.default = pkgs.mkShell {
      buildInputs = with pkgs; [
        androidSdk.androidsdk
        jdk17
        kotlin
        gradle
        ffmpeg
        maestro
      ];

      ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
      MAESTRO_CLI_NO_ANALYTICS = "1";
      MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED = "true";

      shellHook = ''
        echo "🎬 Stream Studio KMP Dev Environment"
        java -version
      '';
    };
  };
}
