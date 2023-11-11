import subprocess
import sys

source_string = """
# *** DO NOT EDIT THESE LINES. FOLLOWING PROPERTIES ARE SET BY BUILD SCRIPT. see build_script.py
# Existent IDE versions can be found in the following repos:
# https://www.jetbrains.com/intellij-repository/releases/
# https://www.jetbrains.com/intellij-repository/snapshots/
# please see https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html for description
# 
pluginGroup = com.github.manu156
pluginName = jpql2sql
pluginVersion=1.1.1
pluginRepositoryUrl = https://github.com/manu156/jpqltosql

"""

ic_versions = [
    {
        "targetIdePlatform": "IC",
        "ideaVersion": "2022.2",
        "sinceBuildPluginXml": "222",
        "untilBuildPluginXml": "222.*"
    },
    {
        "targetIdePlatform": "IC",
        "ideaVersion": "2022.3",
        "sinceBuildPluginXml": "223",
        "untilBuildPluginXml": "223.*"
    },
    {
        "targetIdePlatform": "IC",
        "ideaVersion": "2023.1",
        "sinceBuildPluginXml": "231",
        "untilBuildPluginXml": "231.*"
    },
    {
        "targetIdePlatform": "IC",
        "ideaVersion": "232.7754-EAP-CANDIDATE-SNAPSHOT",
        "sinceBuildPluginXml": "231",
        "untilBuildPluginXml": "233.*"
    }
]

iu_versions = [
    {
        "targetIdePlatform": "IU",
        "ideaVersion": "2022.2",
        "sinceBuildPluginXml": "222",
        "untilBuildPluginXml": "222.*"
    },
    {
        "targetIdePlatform": "IU",
        "ideaVersion": "2022.3",
        "sinceBuildPluginXml": "223",
        "untilBuildPluginXml": "223.*"
    },
    {
        "targetIdePlatform": "IU",
        "ideaVersion": "2023.1",
        "sinceBuildPluginXml": "231",
        "untilBuildPluginXml": "231.*"
    },
    {
        "targetIdePlatform": "IU",
        "ideaVersion": "2023.2",
        "sinceBuildPluginXml": "232",
        "untilBuildPluginXml": "233.*"
    }
]

properties_file = "gradle.properties"

if __name__ == '__main__':
    build_type = sys.argv[1]
    token = sys.argv[2]
    command = "./gradlew buildPlugin -Dorg.gradle.project.intellijPublishToken=" + token
    if "all" == build_type:
        versions = ic_versions + iu_versions
    elif "ic" == build_type:
        versions = ic_versions
    elif "iu" == build_type:
        versions = iu_versions
    else:
        exit(255)

    for ver in versions:
        print("Building for:", ver.get("targetIdePlatform"), ver.get("ideaVersion"))
        with open(properties_file, "w") as f:
            f.write(source_string)
            for k, v in ver.items():
                f.write("\n" + k + "=" + v)

        ret = subprocess.run(command, capture_output=True, shell=True)
        if 0 != ret.returncode:
            print("build failed for", ver.get("targetIdePlatform"), ver.get("ideaVersion"))
            print(ret.stdout.decode())
        else:
            print("build success")
