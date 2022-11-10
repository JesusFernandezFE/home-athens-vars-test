#!/usr/bin/env groovy

import static athens.utils.GlobalParams.*
import static athens.utils.UtilityFunctions.*

import groovy.transform.Field

// This library function is a 'manager' for determining which pre-build scripts should be run when executing
// a specific pipeline.  It looks for specific options in the params Map, and if they are present, tests for 
// true/false status to determine whether the associated script block should be executed.  (If the option is 
// not contained in the params set, this automatically counts as a 'false'.)


def doPrebuildFullClean()
{
	echo ">>> Running Pre-Build Script: doPrebuildFullClean() - PROJECT_ROOT: '${PROJECT_ROOT}'"

	// Clear out the output directories
	powershell """
		if (Test-Path -Path  "${PROJECT_ROOT}\\OutBuild") {
   		 	Write-Host "Removing: ${PROJECT_ROOT}\\OutBuild"
    			Remove-Item -Path "${PROJECT_ROOT}\\OutBuild" -Force -Recurse
		}
		if (Test-Path -Path  "${PROJECT_ROOT}\\Output") {
 		   	Write-Host "Removing: ${PROJECT_ROOT}\\Output"
  		  	Remove-Item -Path "${PROJECT_ROOT}\\Output" -Force -Recurse
		}
	"""

	// Clear out the platform-specific output directories
	if(BUILD_PLATFORM == 'Steam')
	{
		powershell """
			if (Test-Path -Path  "${PROJECT_ROOT}\\SteamOutput") {
					Write-Host "Removing: ${PROJECT_ROOT}\\SteamOutput"
				Remove-Item -Path "${PROJECT_ROOT}\\SteamOutput" -Force -Recurse
			}
			if (Test-Path -Path  "${PROJECT_ROOT}\\SteamUpload") {
				Write-Host "Removing: ${PROJECT_ROOT}\\SteamUpload"
				Remove-Item -Path "${PROJECT_ROOT}\\SteamUpload" -Force -Recurse
			}
		"""
	}
	else if(BUILD_PLATFORM == 'MSIXVC')
	{
		powershell """
			if (Test-Path -Path  "${PROJECT_ROOT}\\MSIXVC_Upload") {
					Write-Host "Removing: ${PROJECT_ROOT}\\MSIXVC_Upload"
					Remove-Item -Path "${PROJECT_ROOT}\\MSIXVC_Upload" -Force -Recurse
			}
		"""
	}

	// Clear out the intermediate directory
	powershell """
		if (Test-Path -Path  "${PROJECT_ROOT}\\intermediate") {
    			Write-Host "Removing: ${PROJECT_ROOT}\\intermediate"
    			Remove-Item -Path "${PROJECT_ROOT}\\intermediate" -Force -Recurse
		}
	"""
}

def clearShaderCache()
{
	echo ">>> Running Pre-Build Script: clearShaderCache()"

	// CYPHTODO: ARE WE SURE THIS SCRIPT IS BEING EXECUTED?
	bat 'if exist "C:\\ProgramData\\Age of Mythology DE\\" rmdir "C:\\ProgramData\\Age of Mythology DE\\" /q /s'
}

def doPrebuildLegacy()
{
	echo ">>> Running Pre-Build Script: doPrebuildLegacy()"

	powershell '''Write-Host "Setting shader files to writable"
		Set-ItemProperty -Path "$ENV:ATHENS_LEGACY_ROOT\\shaders\\*.*" -Name IsReadOnly -Value $false

		Write-Host "Setting skyboxlabs.vdf to writable"
		Set-ItemProperty -Path "$ENV:ATHENS_LEGACY_ROOT\\src\\vsign\\skyboxlabs.vdf" -Name IsReadOnly -Value $false

		Write-Host "Setting AoMX.exe to writable"
		Set-ItemProperty -Path "$ENV:ATHENS_LEGACY_ROOT\\AoMX.exe" -Name IsReadOnly -Value $false
	'''
}


def RunScript(String scriptFlag, def scriptValue) 
{	
	echo ">>> [PRE] RunScript() - ScriptFlag: '${scriptFlag}'  ScriptVal: '${scriptValue}'"

	if(scriptFlag == "fullClean") {
		doPrebuildFullClean()	
	}
	else if(scriptFlag == "clearShaderCache") {
		clearShaderCache()
	}
	else if(scriptFlag == "legacyPreBuild") {
		doPrebuildLegacy()
	}
	else {
		echo ">>> [PRE] ERROR: Could not execute script '${scriptFlag}' - no function found for this script in 'runPreBuildScripts.groovy'"
	}
}


def RunScriptsOnBuilds(def buildTarget) {

	echo ">>> [PRE] RunScriptsOnBuilds() - buildTarget: ${buildTarget}"

	SetTargetGlobalParam(buildTarget.target)

	def platforms = buildTarget.get("platforms")

	// Run the pre-build scripts for each platform (in reality this is likely to be only Steam OR MSIXVC)
	for (int i=0; i < platforms.size(); i++) {

		String platform = platforms[i];

		if((platform != 'Steam') && (platform != 'MSIXVC') && (platform != 'Win32'))
		{
			echo "RunScriptsOnBuilds() - Unknown build platform: " + platform
			continue;
		}

		SetPlatformGlobalParams(platform)

		echo ">>> [PRE] RunScriptsOnBuilds() - Scripts to run: ${SCRIPTS_PREBUILD}"

		SCRIPTS_PREBUILD.each { scriptFlag ->
			// Note: String params will be co-erced to 'true' for the check below
			if(scriptFlag.value) {
				echo ">>> Script flag key: '" + scriptFlag.key + "'  Script flag val: '" + scriptFlag.value
				RunScript(scriptFlag.key, scriptFlag.value)
			}
		}
	}

	echo ">>> Pre-Build Scripts are complete"
}


def call(Map params) {

	echo ">>> Running Pre-Build Scripts"

	boolean buildTargetHasPrebuildScripts = false
	VS_BUILD_TARGETS.each { buildTarget ->
		if(buildTarget.runPreBuildScripts)
			buildTargetHasPrebuildScripts = true
	}

	echo (buildTargetHasPrebuildScripts ? ">>> At least one build target has pre-build scripts!" : ">>> No build target has any pre-build scripts")		// JUST FOR DEBUG

	if(!SCRIPT_BUILD_TARGETS && !buildTargetHasPrebuildScripts) {
		echo ">>> No Build Targets defined for pre-build scripts to run on, skipping this step"
		return
	}

	if(!SCRIPTS_PREBUILD && !buildTargetHasPrebuildScripts) {
		echo ">>> No Pre-Build Scripts defined, skipping this step"
		return
	}

	echo ">>> Pre-Build Script flags: ${SCRIPTS_PREBUILD}"

	if(buildTargetHasPrebuildScripts) {
		// NEW SYSTEM
		VS_BUILD_TARGETS.each { buildTarget ->
			if(buildTarget.runPreBuildScripts) {
				ClearBuildGlobalParams()  	// Clear the global build-related params just to make sure we don't accidentally use stale data
				SetPreBuildScriptsGlobal(buildTarget.runPreBuildScripts)
				RunScriptsOnBuilds(buildTarget)
			}
		}
		SetPreBuildScriptsGlobal(null)
	}
	else {
		// OLD SYSTEM
		SCRIPT_BUILD_TARGETS.each { buildTarget ->
			ClearBuildGlobalParams()  	// Clear the global build-related params just to make sure we don't accidentally use stale data
			RunScriptsOnBuilds(buildTarget)
		}
	}
	
	ClearBuildGlobalParams()  	// Clear the global build-related params just to make sure we don't accidentally use stale data

	echo ">>> Finished running Pre-Build Scripts"
}




