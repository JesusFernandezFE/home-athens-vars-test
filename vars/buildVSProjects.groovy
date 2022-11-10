#!/usr/bin/env groovy

import static athens.utils.GlobalParams.*
import static athens.utils.UtilityFunctions.*


def BuildTarget()
{
	echo ">>> Calling 'BuildTarget()' with build params:"
	PrintGlobalParams("BUILD")

	def buildWithSLN = { SLN_PATH ->
		echo ">> Building from SLN: '${SLN_PATH}'"
		
		def msbuild = "C:/Program Files/Microsoft Visual Studio/2022/Community/Msbuild/Current/Bin/MSBuild.exe"
		def exitStatus = bat "\"${msbuild}\" intermediate/Steam/Athens.sln /t:\"Build\" /p:configuration=\"Playtest\"" 

		if (exitStatus != null){
			currentBuild.result = 'FAILURE'
			echo ">>> BuildTarget() [${BUILD_PLATFORM}, ${BUILD_CONFIG}] - Exit Status: ${exitStatus}"
			error 'build failed'
		}
		else {
			echo ">>> BuildTarget() [${BUILD_PLATFORM}, ${BUILD_CONFIG}] - Exit Status: no errors."
		}
	}

	if(BUILD_PLATFORM_CONFIG == 'Win32')
	{
		String slnPath = "${ATHENS_LEGACY_ROOT}\\src\\rts3\\rts3.sln"
		buildWithSLN(slnPath)
	}
	else {
		String slnPath = "${PROJECT_ROOT}\\intermediate\\${BUILD_PLATFORM_PATH}\\athens.sln"
		buildWithSLN(slnPath)
	}
}

def DoBuildsForTarget(Map buildTarget)
{
	echo ">>> Building VS Projects for Target: ${buildTarget}"

	// Copying these arrays into local vars prevents later issues with trying to access arrays within a map 
	def platforms = buildTarget.get("platforms")
	def configs = buildTarget.get("configs")

    for (int i=0; i < platforms.size(); i++) {

        for (int j=0; j < configs.size(); j++) {

			SetBuildGlobalParams(buildTarget.target, configs[j], platforms[i])
            BuildTarget()
        }
    }
}


def call(Map params) {
	echo ">>> Building VS Projects"

	VS_BUILD_TARGETS.each { buildTarget ->
		ClearBuildGlobalParams()  	// Clear the global build-related params just to make sure we don't accidentally use stale data
		DoBuildsForTarget(buildTarget)
	}

	ClearBuildGlobalParams()  	// Clear the global build-related params just to make sure we don't accidentally use stale data

	echo ">>> Finished Building VS Projects"
}





