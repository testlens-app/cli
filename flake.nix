{
  description = "Dev shell for building the testlens CLI native image with GraalVM musl";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            graalvmPackages.graalvm-ce-musl
          ];

          JAVA_HOME = pkgs.graalvmPackages.graalvm-ce-musl;
          GRAALVM_HOME = pkgs.graalvmPackages.graalvm-ce-musl;
        };
      }
    );
}
