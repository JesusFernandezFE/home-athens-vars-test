#!/usr/bin/env groovy

import static athens.utils.GlobalParams.*
import static athens.utils.UtilityFunctions.*

import groovy.transform.Field

// This library function is a 'manager' for determining which post-build scripts should be run when executing
// a specific pipeline.  It looks for specific options in the params Map, and if they are present, tests for 
// true/false status to determine whether the associated script block should be executed.  (If the option is 
// not contained in the params set, this automatically counts as a 'false'.)


// CYPHTODO: Temp hack for storing the test harness test set list - will fix this properly later,
// just want to get it working for today.
@Field testSetList = []
@Field testHarnessParams = []

def buildTextureCache()
{
	echo ">>> Running Post-Build Script: buildTextureCache()"

	// Clear out the output directories
						powershell """
							Write-Host "Project root: 'E:\\Athens"
							
							Write-Host "Setting streaming data files to writable"
							Set-ItemProperty -Path "E:\\Athens\\Game\\Data\\streamingdata\\*.*" -Name IsReadOnly -Value \$false

							Write-Host "Building texture cache"
							cmd.exe /c "E:\\Athens\\scripts\\BuildScripts\\BuildTextureCache.bat" -enviro "E:\\Athens\\"

							Write-Host "Setting streaming data back to read only"
							Set-ItemProperty -Path "E:\\Athens\\Game\\Data\\streamingdata\\*.*" -Name IsReadOnly -Value \$true
						"""

}

def buildModelCache()
{
	echo ">>> Running Post-Build Script: buildModelCache()"

	// NOTE: 'build_model_cache.py' is used by both the tools project and the actual game project, so cannot be moved
	// from 'Development/Scripts' to the Jenkins repo, and we have to call it from the Development root.

	// CYPHTODO: THE 'cd' COMMAND IS REDUNDANT CONSIDERING THAT WE'RE RUNNING SCRIPT WITH ABSOLTE PATH, CHECK THAT SCRIPT WORKS IF IT'S REMOVED
		bat '''
			cd E:\\Athens
			Scripts\\python\\python.exe Scripts\\build_model_cache.py	
		'''

}

def copyBuildFilesToOutDir()
{
	echo ">>> Running Post-Build Script: copyBuildFilesToOutDir()"

		bat '''
			set OutFolder="E:\\Athens\\OutBuild"
			if not exist %OutFolder% (
				mkdir %OutFolder% 
			)

			echo Copying Exe and other files
			xcopy  /Y /D E:\\Athens\\Scripts\\RelicLink\\BattleServer.exe" "%OutFolder%"
			xcopy  /Y /D E:\\Athens\\Scripts\\Mastering\\xboxservices.config" "%OutFolder%"
			xcopy  /Y /D E:\\Athens\\Scripts\\Mastering\\TestWebClient.exe" "%OutFolder%"
		'''
		
		// Copy the game .exe (and .pdb, for Steam builds) from the appropriate platform directory in 'intermediate'
		if(BUILD_PLATFORM == "Steam") {
			bat '''
				set OutFolder="E:\\Athens\\OutBuild"
				xcopy  /Y "E:\\Athens\\intermediate\\Steam\\age3\\%BLD_CONFIG%\\athens.exe" "%OutFolder%"
				xcopy  /Y "E:\\Athens\\intermediate\\Steam\\age3\\%BLD_CONFIG%\\athens.pdb" "%OutFolder%"
			'''
		}
		
		if(BUILD_PLATFORM == "MSIXVC") {
			bat '''
				set OutFolder="%PROJ_ROOT%\\OutBuild"
				xcopy  /Y  "%PROJ_ROOT%\\intermediate\\Gaming.Desktop\\age3\\%BLD_CONFIG%\\athens.exe" "%OutFolder%"
			'''
		}

		bat '''
			set OutFolder="E:\\Athens\\OutBuild"
			echo D | xcopy /Y /D "E:\\Athens\\Tools\\TMModelViewer.exe" "%OutFolder%\\Tools"
			echo D | xcopy /Y /D "E:\\Athens\\Tools\\Data" "%OutFolder%\\Tools\\Data"
		'''

}

def signExecutables()
{
	echo ">>> Running Post-Build Script: signExecutables()"

	withEnv(["PROJ_ROOT=${PROJECT_ROOT}", "PROJECT_SCRIPTS=${PROJECT_SCRIPT_ROOT}"]) {
		bat 'E:\\Athens\\Scripts\\BuildScripts\\SignExecutables.bat" -packageRoot "E:\\Athens\\OutBuild"'
	}
}

def packageBuild()
{
	echo ">>> Running Post-Build Script: packageBuild()"
	
	String packageCommandFlags
	
	if(BUILD_PLATFORM == "Steam") {
		packageCommandFlags = "\"-steam\" \"-testharness\" \"-bugsplat\""
	}
	if(BUILD_PLATFORM == "MSIXVC") {
		packageCommandFlags = " \"-bugsplat\""
	}	
	


		bat '''
			echo pwsh -ExecutionPolicy Bypass -File "E:\\Athens\\Scripts\\PackageBuild_MT.ps1" "OutBuild" E:\\Athens "BarPatchDir\\Playtest" %PACKAGE_FLAGS% 

			pwsh -ExecutionPolicy Bypass -File E:\\Athens\\Scripts\\PackageBuild_MT.ps1" "OutBuild" E:\\Athens "BarPatchDir\\Playtest" %PACKAGE_FLAGS%
		'''

}

def buildShaderCache()
{
    echo ">>> Running Post-Build Script: buildShaderCache()"
			bat '''
				cd E:\\Athens
				if not exist E:\\Athens\\Output\\" mkdir E:\\Athens\\Output
				E:\\Athens\\intermediate\\Steam\\age3\\Playtest\\athens.exe -ArgumentList '-populateShaderCache=E:\\Athens\\Output -dx12' -WorkingDirectory E:\\Athens -NoNewWindow -PassThru -Wait
			'''
}

def uploadBuildToSteam(String config)
{  
	echo ">>> Running Post-Build Script: uploadBuildToSteam(), config: '${config}'"

	if(BUILD_PLATFORM != 'Steam') {
		echo ">>> ERROR: Trying to run 'uploadBuildToSteam()' script on platform '${BUILD_PLATFORM}'"
		return
	}

	if(config.isEmpty()) {
		// Shouldn't be able to get here with an empty config param, but checking just in case...
		echo ">>> ERROR: Cannot run 'uploadBuildToSteam()' script without a Config specified"
		return
	}

			echo '''
					cd "E:\\Athens\\Scripts\\Steam"
					set PASSWORD=fe_build_machine001"
					set USER="Build2ExtraHouses"
				'''

			bat '''
				cd "E:\\Athens\\Scripts\\Steam"
				set PASSWORD="fe_build_machine001"
				set USER="Build2ExtraHouses"

				FE_steam.bat 
	'''
}



def clearShaderCache()
{
	echo ">>> Running Post-Build Script: clearShaderCache()"

	bat 'if exist "C:\\ProgramData\\Age of Mythology DE\\" rmdir "C:\\ProgramData\\Age of Mythology DE\\" /q /s'
}



def call() {
    buildTextureCache()
    buildModelCache()
    copyBuildFilesToOutDir()
    signExecutables()
    packageBuild()
    buildShaderCache()
    uploadBuildToSteam("Playtest")
}


