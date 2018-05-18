#!/bin/sh

set -e

OC_DISTS=(admin allinone ingest presentation worker worker-encoding worker-light)

rebuild_assemblies() {
  cd "assemblies"
  mvn install
  cd -
}

check_assembly() {
  assembly_name="$1"
  assembly_path="$2"
  assembly_dir="${assembly_path%/*}"
  assembly_ok="true"

  tar -xf "$assembly_path" -C "$assembly_dir" "opencast-dist-$dist/etc/config.properties"
  # test javax.annotation package in config.properties
  if [ "0" -eq "$(grep -c 'javax.annotation' $assembly_dir/opencast-dist-$dist/etc/config.properties)" ]; then
    assembly_ok="false"
  fi
  rm -rf "$assembly_dir/opencast-dist-$dist"

  if [ "true" == "$assembly_ok" ]; then
    return 0
  else
    return 1
  fi
}


assemblies_ok="false"
pass="0"

while [ "false" == "$assemblies_ok" ] && [ "5" -gt "$pass" ]
do
  pass="$((pass + 1))"
  assemblies_ok="true"

  for dist in "${OC_DISTS[@]}"; do
    test -f build/opencast-dist-$dist-[0-9]*.tar.gz || continue
    for dist_assembly in build/opencast-dist-$dist-[0-9]*.tar.gz; do
      check_assembly "$dist" "$dist_assembly" || assemblies_ok="false"
      if [ "false" == "$assemblies_ok" ]; then
        rebuild_assemblies
        break
      fi
    done
  done
done

if [ "false" == "$assemblies_ok" ]; then
  echo "Opencast assemblies aren't wired correctly" >&2
  exit 1
fi
