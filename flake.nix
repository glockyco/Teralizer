{
  description = "Teralizer: test generalization pipeline and its evaluation";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "aarch64-darwin"
        "x86_64-darwin"
        "aarch64-linux"
        "x86_64-linux"
      ];

      forAllSystems =
        f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell {
          # Java 8 is not a preference. `build.gradle` targets 1.8, jpf-core reads
          # class files through its own bytecode model and rejects anything newer,
          # and the generated property tests compile against the same level.
          #
          # Maven is invoked as a bare `mvn` by BuildClasspathResolver,
          # ProjectBuildTask, TestExecutionTask and both data-collection tasks, so
          # it has to resolve on PATH rather than through the Gradle wrapper. It
          # carries its own JDK unless told otherwise, which would silently build
          # target projects at the wrong level.
          packages = [
            pkgs.zulu8
            (pkgs.maven.override { jdk_headless = pkgs.zulu8; })
            pkgs.postgresql_17
            pkgs.uv
            pkgs.git-lfs
          ];

          JAVA_HOME = "${pkgs.zulu8}";
        };
      });
    };
}
