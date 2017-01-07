#!/bin/bash
buildSpecsDirectory="/home/klocwork/kw_build_specs"
tablesDirectory="/home/klocwork/kwtables"
klocworkProject="dcg_security-privacyca"
klocworkServerUrl="https://klocwork-jf18.devtools.intel.com:8160"

initialize() {
  mkdir -p "${buildSpecsDirectory}"
  mkdir -p "${tablesDirectory}"
}

generateBuildSpecs() {
  ant ready clean
  (cd features && kwmaven --output "${buildSpecsDirectory}/privacyca.out" -DskipTests=true install)
}

buildProject() {
  kwbuildproject --url "${klocworkServerUrl}/${klocworkProject}" --tables-directory "${tablesDirectory}" --force "${buildSpecsDirectory}/privacyca.out"
}

uploadResults() {
  kwadmin --url "${klocworkServerUrl}" load "${klocworkProject}" "${tablesDirectory}"
}

initialize
generateBuildSpecs
buildProject
uploadResults