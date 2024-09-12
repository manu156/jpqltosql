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
pluginVersion=1.3.1
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
        "ideaVersion": "2023.2",
        "sinceBuildPluginXml": "232",
        "untilBuildPluginXml": "232.*"
    },
    {
        "targetIdePlatform": "IC",
        "ideaVersion": "2023.3",
        "sinceBuildPluginXml": "233",
        "untilBuildPluginXml": "233.*"
    },
    {
        "targetIdePlatform": "IC",
        "ideaVersion": "2024.1",
        "sinceBuildPluginXml": "241",
        "untilBuildPluginXml": "241.*"
    },
    {
        "targetIdePlatform": "IC",
        "ideaVersion": "2024.2.1",
        "sinceBuildPluginXml": "242",
        "untilBuildPluginXml": "242.*"
    },
    {
         "targetIdePlatform": "IC",
         "ideaVersion": "243.12818-EAP-CANDIDATE-SNAPSHOT",
         "sinceBuildPluginXml": "243",
         "untilBuildPluginXml": "243.*"
    }
    # for snapshots see https://www.jetbrains.com/intellij-repository/snapshots
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
        "untilBuildPluginXml": "232.*"
    },
    {
        "targetIdePlatform": "IU",
        "ideaVersion": "2023.3",
        "sinceBuildPluginXml": "233",
        "untilBuildPluginXml": "233.*"
    },
    {
        "targetIdePlatform": "IU",
        "ideaVersion": "2024.1",
        "sinceBuildPluginXml": "241",
        "untilBuildPluginXml": "241.*"
    },
    {
        "targetIdePlatform": "IU",
        "ideaVersion": "2024.2.1",
        "sinceBuildPluginXml": "242",
        "untilBuildPluginXml": "242.*"
    },
    {
        "targetIdePlatform": "IU",
        "ideaVersion": "243.12818-EAP-CANDIDATE-SNAPSHOT",
        "sinceBuildPluginXml": "243",
        "untilBuildPluginXml": "243.*"
    }
    # for snapshots see https://www.jetbrains.com/intellij-repository/snapshots
]

properties_file = "gradle.properties"

if __name__ == '__main__':
    build_type = sys.argv[1]
    token = sys.argv[2]
    target_ver = sys.argv[3]
    command = "./gradlew buildPlugin -Dorg.gradle.project.intellijPublishToken=" + token
    if "all" == build_type:
        versions = ic_versions + iu_versions
    elif "ic" == build_type:
        versions = ic_versions
    elif "iu" == build_type:
        versions = iu_versions
    else:
        exit(255)

    if target_ver is not None:
        versions = [i for i in versions if i["ideaVersion"] == target_ver]

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
