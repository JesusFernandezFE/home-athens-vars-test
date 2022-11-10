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
			
			Write-Host "Setting streaming data files to writable"
			Set-ItemProperty -Path "E:\\Athens\\Game\\Data\\streamingdata\\*.*" -Name IsReadOnly -Value \$false

			Write-Host "Building texture cache"
			cmd.exe /c "E:\\Athens\\scripts\\BuildTextureCache.bat" -enviro "E:\\Athens}\\"

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
			E:\\Athens\\Scripts\\python\\python.exe E:\\Athens\\Scripts\\build_model_cache.py	
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
			xcopy  /Y /D E:\\Athens\\Scripts\\RelicLink\\BattleServer.exe %OutFolder%
			xcopy  /Y /D E:\\Athens\\Scripts\\Mastering\\xboxservices.config %OutFolder%
			xcopy  /Y /D E:\\Athens\\Scripts\\Mastering\\TestWebClient.exe" %OutFolder%
			xcopy  /Y E:\\Athens\\intermediate\\Steam\\age3\\Playtest\\athens.exe %OutFolder%
			xcopy  /Y E:\\Athens\\intermediate\\Steam\\age3\\Playtest\\athens.pdb %OutFolder%
			xcopy /Y /D "E:\\Athens\\amd_ags_x64.dll" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\bink2w64.dll" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\BugSplat64.dll" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\steam_api64.dll" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\Scripts\\URLHelper\\AOEURLHelper.exe" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\Scripts\\URLHelper\\AoEURLInstaller_Steam.exe" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\GFSDK_Aftermath_Lib.x64.dll" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\WPF_Test.dll" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\WPFRender_DX12_Test.dll" "E:\\Athens\\OutBuild"
			xcopy /Y /D "E:\\Athens\\D3D12_Agility\\D3D12Core.dll" "E:\\Athens\\OutBuild"
			echo D | xcopy /Y /D "E:\\Athens\\Tools\\TMModelViewer.exe" "%OutFolder%\\Tools"
			echo D | xcopy /Y /D "E:\\Athens\\Tools\\Data" "%OutFolder%\\Tools\\Data"
		'''
		
}


def signExecutables()
{
	echo ">>> Running Post-Build Script: signExecutables()"
	bat '"E:\\Athens\\Scripts\\SignExecutables.bat" -packageRoot "E:\\Athens\\OutBuild"'

}

def packageBuild()
{
	echo ">>> Running Post-Build Script: packageBuild()"
	
	String packageCommandFlags
	packageCommandFlags = " \"-steam\" \"-testharness\" \"-bugsplat\" \"-debug\" "

	
	withEnv(["PACKAGE_FLAGS=${packageCommandFlags}"]) {

		bat '''
			echo pwsh -ExecutionPolicy Bypass -File "E:\\Athens\\Scripts\\PackageBuild_MT.ps1" "OutBuild" "E:\\Athens" "BarPatchDir\\Playtest" %PACKAGE_FLAGS% 

			"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -ExecutionPolicy Bypass -File "E:\\Athens\\Scripts\\PackageBuild_MT.ps1" "OutBuild" "E:\\Athens" "BarPatchDir\\Playtest" %PACKAGE_FLAGS%
		'''
	}
}

def buildShaderCache(ArrayList testParams)
{

	echo ">>> Running Post-Build Script: buildShaderCache()"

	bat '''
		cd E:\\Athens
		if not exist "E:\\Athens\\Output\\" mkdir E:\\Athens\\Output
		E:\\Athens\\intermediate\\Steam\\age3\\Playtest\\athens.exe -ArgumentList '-populateShaderCache=E:\\Athens\\Output -dx12' -WorkingDirectory E:\\Athens -NoNewWindow -PassThru -Wait
	'''

}

def uploadBuildToSteam(String config)
{  
	echo ">>> Running Post-Build Script: uploadBuildToSteam(), config: '${config}'"

	withEnv(['P4_CL=${P4_CHANGELIST}'])
	{
				bat '''
					cd "E:\\Athens\\Source\\extlib\\steam\\steamworks_sdk_142\\tools\\ContentBuilder\\builder"
					steamcmd.exe +login "fe_build_machine001" "Build2ExtraHouses" +run_app_build "E:\\Athens\\Scripts\\Steam\\app_build.vdf" +quit" 					
				'''
	}

}


def uploadBuildToMSStore()
{
	echo ">>> Running Post-Build Script: uploadBuildToMSStore()"

	if(BUILD_PLATFORM != 'MSIXVC') {
		echo ">>> ERROR: Trying to run 'uploadBuildToMSStore()' script on platform '${BUILD_PLATFORM}'"	
		return
	}

	// CYPHTODO: DO WE WANT THIS SCRIPT TO RUN FROM DEV SCRIPTS, OR MOVE TO JENKINS SCRIPTS?  IF SO, WE NEED TO
	// MOVE MOST OF THE 'Mastering' SCRIPTS FOLDER TO JENKINS, AND THEN MAKE 'MSIXVCBuild.bat' TAKE A PARAM TO 
	// SPECIFY THE PROJECT ROOT, AS IT HAS A HEAP OF RELATIVE PATHS

	withEnv(["WORKSPACE=${P4_WORKSPACE}", "PROJ_ROOT=${PROJECT_ROOT}", "PROJECT_SCRIPTS=${PROJECT_SCRIPT_ROOT}"]) {
		bat '''
			cd "%PROJ_ROOT%"
			"Scripts\\MSIXVC\\MSIXVCBuild.bat" -packageRoot "Outbuild" -output "MSIXVC_Upload" -client %WORKSPACE%
		'''

		powershell '''
			Write-Output "Hello Chris, first output"
			cd "${PROJECT_SCRIPTS}\\MSIXVC\\PackageUploader-1.4.0\\src"
			.\\PackageUploader.exe RemovePackages -c .\\RemovePackagesInMain.json -s U918Q~v6BqMMlTzWT9aVh6MaXhsCc0jChHnpidbk

			Write-Output "Hello Chris, second output"
			cd "${PROJECT_SCRIPTS}\\MSIXVC\\PackageUploader-1.4.0\\src"
			.\\PackageUploader.exe UploadXvcPackage -c .\\UploadAthensToMain.json -s U918Q~v6BqMMlTzWT9aVh6MaXhsCc0jChHnpidbk

			Write-Output "Hello Chris, third output"
			cd "${PROJECT_SCRIPTS}\\MSIXVC\\PackageUploader-1.4.0\\src"
			.\\PackageUploader.exe PublishPackages -c .\\PublishAthensMainToSandbox26.json -s U918Q~v6BqMMlTzWT9aVh6MaXhsCc0jChHnpidbk

			Write-Output "Hello Chris, forth output"
		'''
	}
}

def runPerforceSubmitExes()
{
	echo ">>> Running Post-Build Script: runPerforceSubmitExes()"

	withEnv(["WORKSPACE=${P4_WORKSPACE}", "PROJECT_SCRIPTS=${PROJECT_SCRIPT_ROOT}", "JENKINS_SCRIPTS=${JENKINS_SCRIPT_ROOT}"]) {
		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'P4-Athens',
		usernameVariable: 'PERFORCE_USERNAME', passwordVariable: 'PERFORCE_PASSWORD']
		])
		{
			bat '''
				cd %PROJECT_SCRIPTS%
				%JENKINS_SCRIPTS%\\python\\python.exe perforce_submit_athens_and_tools.py %PERFORCE_USERNAME% %PERFORCE_PASSWORD% %WORKSPACE%
			'''
		}
	}
}


def uploadArtifacts()
{
	echo ">>> Running Post-Build Script: uploadArtifacts()"
	withEnv(["WORKSPACE=${P4_WORKSPACE}", "PROJ_ROOT=${PROJECT_ROOT}", "PROJECT_SCRIPTS=${PROJECT_SCRIPT_ROOT}", "JENKINS_SCRIPTS=${JENKINS_SCRIPT_ROOT}"]) {
		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'P4-Athens',
			usernameVariable: 'PERFORCE_USERNAME', passwordVariable: 'PERFORCE_PASSWORD']
		])
		{
			bat '''
				cd %PROJECT_SCRIPTS%
				%JENKINS_SCRIPTS%\\python\\python.exe bugsplat_upload_artifacts.py %PERFORCE_USERNAME% %PERFORCE_PASSWORD% %WORKSPACE% %PROJ_ROOT%
			'''
		}
	}
}


def integrateStreams(def streamsToIntegrate)
{
	echo ">>> Running Post-Build Script: integrateStreams()"

	for (int i=0; i < streamsToIntegrate.size(); i++) {
		
		def streamIntegratePair = streamsToIntegrate[i]
		
		if(streamIntegratePair.size() != 2) {
			error(">>> ERROR: 'streamsToIntegrate' params must have exactly two params (a source stream and a destination stream).  Params: '${streamIntegratePair}'")
		}

		def fromStream = "//stream/Athens/" + streamIntegratePair[0]
		def toStream = "//stream/Athens/" + streamIntegratePair[1]
		echo ">>> Integrating from stream '${fromStream}' to stream '${toStream}' in workspace '${P4_WORKSPACE}'"

		withEnv(["WORKSPACE=${P4_WORKSPACE}", "FROM_STREAM=${fromStream}", "TO_STREAM=${toStream}"]) {
			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'P4-Athens',
			usernameVariable: 'PERFORCE_USERNAME', passwordVariable: 'PERFORCE_PASSWORD']])
			{	
				bat '''
					cd %JENKINS_SCRIPT_ROOT%
					python\\python.exe %PROJECT_SCRIPT_ROOT%\\perforce_merge_streams.py %PERFORCE_USERNAME% %PERFORCE_PASSWORD% %WORKSPACE% %FROM_STREAM% %TO_STREAM%
				'''
			}
		}
	}
}


def clearShaderCache()
{
	echo ">>> Running Post-Build Script: clearShaderCache()"

	// CYPHTODO: ARE WE SURE THIS SCRIPT IS BEING EXECUTED?
	bat 'if exist "C:\\ProgramData\\Age of Mythology DE\\" rmdir "C:\\ProgramData\\Age of Mythology DE\\" /q /s'
}

def runStabilityTestForPlatforms(String scriptParam, ArrayList testParams)
{
	echo ">>> Running Post-Build Script: runStabilityTest()"

	String platform = testParams[0]
	String config = testParams[1]

	echo ">>> runStabilityTestForPlatforms() - platform: '${platform}'  config: '${config}'"

//	clearShaderCache()	// CYPH TODO: NEED TO FIX PARAMS IN JENKINSFILES, ADD 'clearShaderCache' WHERE NEEDED

	// Note: This is a call to another .groovy script, not another function in this .groovy file
	runStabilityTests(scriptParam, platform, config)
}

def runTestHarnessTestsForPlatforms(ArrayList testParams)
{
	echo ">>> Running Post-Build Script: runTestHarnessTestsForPlatforms() [TEMPORARILY DISABLED]"
/*
	// CYPHTODO: THIS IS COMMENTED OUT UNTIL I CAN FIGURE OUT THE DRATTED RETURN VALUE ISSUE THAT IT'S CURRENTLY REDLIGHTING ON
	echo ">>> Running Post-Build Script: runTestHarnessTestsForPlatforms()"	

	def platform = testParams[0]
	def config = testParams[1]

	echo ">>> runTestHarnessTestsForPlatforms() - platform: '${platform}'  config: '${config}'"

	clearShaderCache()

	// Note: This is a call to another .groovy script, not another function in this .groovy file
	runTestHarnessTestSets(platform, config, testSetList, testHarnessParams)
*/
}

def runLegacyPostBuild()
{
	echo ">>> Running Post-Build Script: runLegacyPostBuild()"

	if(BUILD_PLATFORM != 'Win32') {
		echo ">>> ERROR: Trying to run 'runLegacyPostBuild()' script on platform '${BUILD_PLATFORM}'"
		return
	}
	
    STEAM_CREDS = credentials('c569f553-fc3d-4d31-ab07-e1d6f826e4a6')
	
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'c569f553-fc3d-4d31-ab07-e1d6f826e4a6',
		usernameVariable: 'STEAM_CREDS_USR', passwordVariable: 'STEAM_CREDS_PSW']])
	{
		bat '''cd %ATHENS_LEGACY_ROOT%\\src\\steam_scripts
		attrib -r *.* /s
		builder\\steamcmd.exe +login %STEAM_CREDS_USR% "%STEAM_CREDS_PSW%" +run_app_build "..\\app-build-266840-dev.vdf" +quit
		attrib +r *.* /s
		'''
	}

	powershell '''Write-Host "Setting shader files to readable"
	Set-ItemProperty -Path "$ENV:ATHENS_LEGACY_ROOT\\shaders\\*.*" -Name IsReadOnly -Value $true

	Write-Host "Setting skyboxlabs.vdf to readable"
	Set-ItemProperty -Path "$ENV:ATHENS_LEGACY_ROOT\\src\\vsign\\skyboxlabs.vdf" -Name IsReadOnly -Value $true

	Write-Host "Setting AoMX.exe to readable"
	Set-ItemProperty -Path "$ENV:ATHENS_LEGACY_ROOT\\AoMX.exe" -Name IsReadOnly -Value $true'''
}


def RunScript(String scriptFlag, def scriptValue) 
{	
	echo ">>> [POST] RunScript() - ScriptFlag: '${scriptFlag}'  ScriptVal: '${scriptValue}'"

	if(scriptFlag == "buildTextureCache") {
		buildTextureCache()	
	}
	else if(scriptFlag == "perforceSubmitExes") {
		runPerforceSubmitExes()	
	}
	else if(scriptFlag == "copyBuildFilesToOutDir") {
		copyBuildFilesToOutDir()
	}
	else if(scriptFlag == "buildModelCache") {
		buildModelCache()	
	}
	else if(scriptFlag == "packageBuild") {
		packageBuild()	
	}
	else if(scriptFlag == "signExecutables") {
		signExecutables()
	}
	else if(scriptFlag == "uploadBuildToSteamConfig") {
		uploadBuildToSteam(scriptValue)
	}
	else if(scriptFlag == "uploadBuildToMSStore") {
		uploadBuildToMSStore()
	}
	else if(scriptFlag == "uploadArtifacts") {
		uploadArtifacts()
	}
	else if(scriptFlag == "legacyPostBuild") {
		runLegacyPostBuild()
	}
	else if(scriptFlag == "streamsToIntegrate") {
		integrateStreams(scriptValue)
	}
	else if(scriptFlag == "testBuildShaderCache") {
		buildShaderCache(scriptValue)
	}
	else if(scriptFlag == "runStabilityTest") {
		runStabilityTestForPlatforms(scriptFlag, scriptValue)
	}
	else if(scriptFlag == "stabilityTestViaBat") {
		runStabilityTestForPlatforms(scriptFlag, scriptValue)
	}
	else if(scriptFlag == "runTestHarnessTests") {
		runTestHarnessTestsForPlatforms(scriptValue)
	}
	else {
		echo ">>> [POST] ERROR: Could not execute script '${scriptFlag}' - no function found for this script in 'runPostBuildScripts.groovy'"
	}
}

// Previously we couldn't control the order of execution of pre- or post-build scripts
// from the Jenkinsfiles, as it was hard-coded in the 'RunScripts()' file, so I've now
// modified the functionally to loop through the scripts in the list and execute them 
// in the set order they were defined
def RunAllScripts() 
{	
	echo ">>> RunAllScripts() - Executing scripts"

	PrintGlobalParams("BUILD")	// CYPH TEST: JUST FOR DEBUGGING, WHILE SWITCHING OVER TO GLOBALS

	echo ">>> [POST] RunScriptsOnBuilds() - Scripts to run: ${SCRIPTS_POSTBUILD}"

	SCRIPTS_POSTBUILD.each { scriptFlag ->
		// Note: String params will be co-erced to 'true' for the check below
		if(scriptFlag.value) {
			echo ">>> Script flag key: '" + scriptFlag.key + "'  Script flag val: '" + scriptFlag.value
			RunScript(scriptFlag.key, scriptFlag.value)
		}
	}
	
	echo ">>> RunAllScripts() - Finished executing scripts on Platform: '${BUILD_PLATFORM}'"
}

def RunScriptsOnBuilds(def buildTarget) {

	echo ">>> [POST] RunScriptsOnBuilds() - buildTarget: ${buildTarget}\nScriptFlags: ${SCRIPTS_POSTBUILD}"

	SetTargetGlobalParam(buildTarget.target)

	if(buildTarget.platforms)	// Note: due to 'Groovy Truth', this will only be true if this param is both 'not null' and 'not empty'
	{
		def platforms = buildTarget.get("platforms")

		echo ">>> Running Post-Build Scripts on platform set: ${platforms}"

		// Run the post-build scripts for each platform (in reality this is likely to be only Steam OR MSIXVC)
		for (int i=0; i < platforms.size(); i++) {

			String platform = platforms[i];

			if((platform != 'Steam') && (platform != 'MSIXVC') && (platform != 'Win32'))
			{
				echo "RunScriptsOnBuilds() - Unknown build platform: " + platform
				continue;
			}

			SetPlatformGlobalParams(platform)

			// CYPH TODO: DOES PRE-BUILD SCRIPTS NEED THIS LOOP TOO?
			def configs = buildTarget.get("configs")
			for (int j = 0; j < configs.size(); j++) {

				SetConfigGlobalParam(configs[j])
				RunAllScripts()
			}
		}
	}
	else
	{
		// Note: It is acceptable to have no 'platform/config' combo specified for a pipeline - some scripts 
		// are able to be run without these params, as long as the build 'target' is still specified
		RunAllScripts()
	}
}


def call(Map params) {

	echo ">>> Running Post-Build Scripts"

	boolean buildTargetHasPostbuildScripts = false	
	VS_BUILD_TARGETS.each { buildTarget ->
		if(buildTarget.runPostBuildScripts)
			buildTargetHasPostbuildScripts = true
	}

	echo (buildTargetHasPostbuildScripts ? ">>> At least one build target has post-build scripts!" : ">>> No build target has any post-build scripts")		// JUST FOR DEBUG

	if(!SCRIPT_BUILD_TARGETS && !buildTargetHasPostbuildScripts) {
		echo ">>> No Build Targets defined for post-build scripts to run on, skipping this step"
		return
	}
	
	if(!SCRIPTS_POSTBUILD && !buildTargetHasPostbuildScripts) {
		echo ">>> No Post-Build Scripts defined, skipping this step"
		return
	}

	PrintGlobalParams()		// CYPH: JUST FOR TESTING

	// CYPHTODO: TEMP HACK TO SHOVE THESE LISTS INTO GLOBALS, UNTIL I FIX THIS UP PROPERLY
	// AND MAKE IT PASS THEM AS PARAMS OR SOMETHING, SOMEHOW, I DUNNO
	if(params.runTestHarnessTestSets != null) {
		testSetList = params.get("runTestHarnessTestSets")
	}
	if(params.testHarnessParams != null) {
		testHarnessParams = params.get("testHarnessParams")
	}
	// END TEMP HACK

	echo ">>> Post-Build Script flags: ${SCRIPTS_POSTBUILD}"

	if(buildTargetHasPostbuildScripts) {
		// NEW SYSTEM
		VS_BUILD_TARGETS.each { buildTarget ->
			if(buildTarget.runPostBuildScripts) {
				ClearBuildGlobalParams()  	// Clear the global build-related params just to make sure we don't accidentally use stale data
				SetPostBuildScriptsGlobal(buildTarget.runPostBuildScripts)
				RunScriptsOnBuilds(buildTarget)
			}
		}
		SetPostBuildScriptsGlobal(null)
	}
	else {
		// OLD SYSTEM
		SCRIPT_BUILD_TARGETS.each { buildTarget ->
			ClearBuildGlobalParams()  	// Clear the global build-related params just to make sure we don't accidentally use stale data
			RunScriptsOnBuilds(buildTarget)
		}
	}


	ClearBuildGlobalParams()  	// Clear the global build-related params just to make sure we don't accidentally use stale data

	echo ">>> Post-Build Scripts are complete"
}


