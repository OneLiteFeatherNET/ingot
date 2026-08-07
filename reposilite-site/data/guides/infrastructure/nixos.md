---
id: nixos
title: NixOS
community: true
---

You can install Ingot on NixOS using the following configuration.

Create a package expression in `/etc/nixos/ingot-bin.nix` file (adjust JDK package,
version and JVM properties to your needs). The `sha256` below is a placeholder: run the
build once and Nix will print the hash it actually got.

```nix
{ pkgs, ... }:
let
  jdk = pkgs.openjdk17_headless;
  stdenv = pkgs.stdenv;
in
stdenv.mkDerivation rec {
  pname = "ingot-bin";
  version = "1.0.0";

  jar = builtins.fetchurl {
    url="https://github.com/OneLiteFeatherNET/ingot/releases/download/v${version}/ingot-${version}.jar";
    sha256="0000000000000000000000000000000000000000000000000000000000000000";
  };

  dontUnpack = true;

  nativeBuildInputs = [ pkgs.makeWrapper ];
  installPhase = ''
    runHook preInstall
    makeWrapper ${jdk}/bin/java $out/bin/ingot \
      --add-flags "-Xmx40m -jar $jar" \
      --set JAVA_HOME ${jdk}
    runHook postInstall
  '';
}
```

Put the Ingot configuration in `/etc/nixos/ingot.nix` (adjust `cfg.user`, `cfg.group` 
etc. to your needs):

```nix
{ config, pkgs, ... }:
let 
  ingot = (import ./ingot-bin.nix { inherit pkgs; }); 
  cfg = { 
    user = "ingot"; 
    group = "ingot"; 
    home = "/var/lib/ingot"; 
    pkg = ingot; 
    port = 8084;
  };
in
{
  environment.systemPackages = [
    cfg.pkg
  ];

  users.groups.${cfg.group} = {
    name = cfg.group;
  };

  users.users.${cfg.user} = {
    isSystemUser = true;
    group = cfg.group;
    home = cfg.home;
    createHome = true;
  };

  systemd.services."ingot" = {
    description = "Ingot - Maven repository";

    wantedBy = [ "multi-user.target" ];

    script = "${cfg.pkg}/bin/ingot --working-directory ${cfg.home} --port ${toString cfg.port}";

    serviceConfig = {
      User = cfg.user;
      Group = cfg.group;
    };
  };
}
```
Add Ingot to NixOS e.g. in `/etc/nixos/configuration.nix`:

```nix
{ config, pkgs, ... }:
{
  imports = [
    # ...
    ./ingot.nix
  ];
  # ...
}
```

### Ingot CLI

The configuration above adds Ingot to the system path, which may be needed to configure 
Ingot. For example:

```bash
systemctl stop ingot.service
runuser -u ingot -g ingot -- ingot --working-directory /var/lib/ingot --port 8084
```

When started from a terminal, Ingot's console can be used to add users and tokens.
