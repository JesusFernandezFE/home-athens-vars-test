#!/usr/bin/env groovy
package athens.utils


class GlobalParams {
	static def gp_script = null
	
	// --- THE SET OF PROJECT GLOBALS ---
	static boolean PIPELINE_REQUIRES_BUILD_STEPS = true
	
    static String P4_WORKSPACE = ""
    static String P4_STREAM = ""

	static String PROJECT_ROOT = ""
	static String PROJECT_SCRIPT_ROOT = ""
	static String JENKINS_SCRIPT_ROOT = ""

    static ArrayList SCRIPT_BUILD_TARGETS = []
    static ArrayList VS_BUILD_TARGETS = []

    static String BUILD_TARGET = ""
    static String BUILD_CONFIG = ""
    static String BUILD_PLATFORM = ""
    static String BUILD_PLATFORM_PATH = ""
    static String BUILD_PLATFORM_CONFIG = ""

	static def SCRIPTS_PREBUILD = null
	static def SCRIPTS_POSTBUILD = null

	static def TESTHARNESS_SETS = null
	static def TESTHARNESS_PARAMS = null

	static boolean SLACK_DO_REPORTING = true
	static def SLACK_PARAMS = null

	static boolean GLOBALS_DETAILED_PRINTS = true

	// --- INIT & PARAM SETTING FUNCTIONS ---

	static def SetScriptForGlobalParams(def script) {
		if(script)
			gp_script = script
	}

	static def InitAndSetGlobalParams(def params, def script = null) {

		if(script)
			gp_script = script
		
		// If the string doesn't exist in the map, we want to return an empty string rather than a null
		// to ensure that the globals retain the String type.
		static getStringIfExists = { key -> 
			return (params.containsKey(key) ? params.get(key) : "")
		}

		static getBoolIfExistsOrKeepCurrent = { key -> 
			return (params.containsKey(key) ? params.get(key) : false)
		}
		
		// This test actually tests for both a 'null' value AND an empty list/map.  If the list/map is empty, 
		// we just want to set the global to null so that it's easier for other functions to test against.
		static getMapValIfExistsAndNotEmpty = { key -> 
			def val = params.get(key)
			return (val ? val : null ) 
		}

		gpEcho("+++ SetInitialGlobalParams() - params: " + params)

		P4_STREAM = getStringIfExists("stream")		
		P4_WORKSPACE = getStringIfExists("workspace")

		SetBuildTargetsFromParams(params)

		JENKINS_SCRIPT_ROOT = gp_script.env.ATHENS_SCRIPTS_ROOT

		PROJECT_ROOT = UtilityFunctions.P4Helpers.GetWorkspaceRoot(P4_WORKSPACE)
		PROJECT_SCRIPT_ROOT = PROJECT_ROOT + "\\Scripts"

		SCRIPTS_PREBUILD = getMapValIfExistsAndNotEmpty("runPreBuildScripts")
		SCRIPTS_POSTBUILD = getMapValIfExistsAndNotEmpty("runPostBuildScripts")

		TESTHARNESS_SETS = getMapValIfExistsAndNotEmpty("runTestHarnessTestSets")
		TESTHARNESS_PARAMS = getMapValIfExistsAndNotEmpty("testHarnessParams")

		SLACK_DO_REPORTING = getBoolIfExistsOrKeepCurrent("runSlackReporting", SLACK_DO_REPORTING)
		SLACK_PARAMS =  getMapValIfExistsAndNotEmpty("slackParams")

		// CYPHTODO: ADD THIS PARAM TO THE JENKINSFILES AT SOME POINT
		GLOBALS_DETAILED_PRINTS = getBoolIfExistsOrKeepCurrent("detailedDebugPrints", GLOBALS_DETAILED_PRINTS)
	}

	static def SetBuildGlobalParams(String target, String config, String platform) {
		gpEcho("+++ Setting BUILD Global Params +++")
		
		BUILD_TARGET = target
		BUILD_CONFIG = config

		SetPlatformGlobalParams(platform)
	}

	static def SetBuildTargetsFromParams(def params) {
		gpEcho("+++ Setting BUILD TARGETS (P4 & SCRIPT) Global Params +++")

		if(params.targets) {
			for(int i = 0; i<params.targets.size(); i++) {
				boolean runScripts = UtilityFunctions.ParamExistsAndIsTrue(params.targets[i], "runScriptsForThisTarget") 
				AddBuildTargetToList(params.targets[i], runScripts)				
			}
		}
		else if(params.target) {
			def buildTarget = [
				target: params.target,
				configs: params.configs,
				platforms: params.platforms	
			]

			AddBuildTargetToList(buildTarget, true)
		}
		
		PrintGlobalParams("BUILD_TARGETS")
	}


	// ---- CYPHTODO: THESE SETTERS ARE TEMPORARY - NEED TO TRY FIXING SETTERS WITH 'setProperty()' WHEN I HAVE TIME

	static def SetConfigGlobalParam(String config) {
	gpEcho("+++ Setting BUILD_CONFIG Global Params to '${config}' +++")
		BUILD_CONFIG = config
	}

	static def SetPlatformGlobalParams(String platform) {
		gpEcho("+++ Setting BUILD-related Global Params +++")

		BUILD_PLATFORM = platform

		if(BUILD_PLATFORM == 'Steam') {
			BUILD_PLATFORM_PATH = 'Steam'
			BUILD_PLATFORM_CONFIG = 'x64'
		}
		else if(BUILD_PLATFORM == 'MSIXVC') {
			BUILD_PLATFORM_PATH = 'Gaming.Desktop'
			BUILD_PLATFORM_CONFIG = 'Gaming.Desktop.x64'
		}
		else if(BUILD_PLATFORM == 'Win32') {
			BUILD_PLATFORM_PATH = ''
			BUILD_PLATFORM_CONFIG = 'Win32'
		}
		else {
			gpEcho("+++ SetPlatformGlobalParams: Unknown BUILD_PLATFORM '${platform}', cannot set BUILD_PLATFORM_PATH")
		}

		PrintGlobalParams("BUILD")
	}

	static def SetTargetGlobalParam(String target) {
	gpEcho("+++ Setting BUILD_TARGET Global Params to '${target}' +++")
		BUILD_TARGET = target
	}

	static def AddBuildTargetToList(def target, boolean scriptTarget) {
		
		gpEcho("+++ Adding target '${target}' to BUILD_TARGET lists in Global Params +++")
		
		VS_BUILD_TARGETS.add(target)
		
		if(scriptTarget)
			SCRIPT_BUILD_TARGETS.add(target)
	}

	static def SetPreBuildScriptsGlobal(def scripts) {
		gpEcho("+++ Setting SCRIPTS_PREBUILD Global Params to '${scripts}' +++")
		SCRIPTS_PREBUILD = scripts
	}

	static def SetPostBuildScriptsGlobal(def scripts) {
		gpEcho("+++ Setting SCRIPTS_POSTBUILD Global Params to '${scripts}' +++")
		SCRIPTS_POSTBUILD = scripts
	}


	// ---- END TEMPORARY SETTERS 

	// As soon as we finish using a set of build params, we want to clear them, to prevent stale
	// data potentially causing non-obvious errors down the line - better to have an error due to 
	// unset data that's much more obvious.
	static def ClearBuildGlobalParams() {
		gpEcho("+++ Clearing all BUILD-related global params!")
		BUILD_TARGET = ""
		BUILD_CONFIG = ""
		BUILD_PLATFORM = ""
		BUILD_PLATFORM_PATH = ""
		BUILD_PLATFORM_CONFIG = ""

		PrintGlobalParams("BUILD")	// CYPH TEST: JUST FOR DEBUGGING, WHILE SWITCHING OVER TO GLOBALS
	}

	static def VerifyBuildGlobalsAreSet() {
		if( (BUILD_TARGET == "") || (BUILD_CONFIG == "") || (BUILD_PLATFORM == "") ) {
			gpErrorAndAbort("GlobalParams: Build Globals are NOT set when they should be")
			return false
		}

		return true
	}

	static def SetPipelineRequiresBuild(boolean requiresBuild) {
		PIPELINE_REQUIRES_BUILD_STEPS = requiresBuild	
	}

	// --- UTILITY FUNCTIONS (MOSTLY FOR DEBUGGING, ETC) ---

	static def gpEcho(String echoStr) {	
		if(gp_script) {
			gp_script.echo(echoStr)
		}
	}

	static def gpError(String echoStr) {	
		if(gp_script) {
			gp_script.echo("+++ UTILS ERROR: ${echoStr}")
		}
	}

	static def gpErrorAndAbort(String echoStr) {	
		if(gp_script) {
			gp_script.error("+++ UTILS ERROR: ${echoStr} [ABORTING PIPELINE]")
		}
	}

	static def PrintGlobalParams(String params = "ALL") {
		
		static getStringParam = { param ->
			return ((param == "") ? "[not set]" : "'${param}'" )
		}
		
		static getMapOrListParam = { param ->
			return ((param == null) ? "[not set]" : param.toString())
		}

		static getBoolParam = { param ->
			return ((param == true) ? "true" : "false")
		}		

		if(gp_script == null) {
			return;
		}
		
		boolean printAll = (params == "ALL")
		
  		String paramStr = "\n-------------------------------------------------"
		paramStr += "\n++++++++ PrintGlobalParams ['${params}'] ++++++++"

		if(printAll || params == "P4") {
			paramStr += "\n--------- P4 params: ---------"
			paramStr += "\n+++ P4_WORKSPACE: " + getStringParam(P4_WORKSPACE)
			paramStr += "\n+++ P4_STREAM: " + getStringParam(P4_STREAM)
		}
		if(printAll || params == "ROOTS") {
			paramStr += "\n--------- ROOT params: ---------"
			paramStr += "\n+++ PROJECT_ROOT: " + getStringParam(PROJECT_ROOT)
			paramStr += "\n+++ PROJECT_SCRIPT_ROOT: " + getStringParam(PROJECT_SCRIPT_ROOT)
			paramStr += "\n+++ JENKINS_SCRIPT_ROOT: " + getStringParam(JENKINS_SCRIPT_ROOT)
		}
		if(printAll || params == "BUILD_TARGETS") {
			paramStr += "\n--------- P4 params: ---------"
			paramStr += "\n+++ SCRIPT_BUILD_TARGETS: " + getMapOrListParam(SCRIPT_BUILD_TARGETS)
			paramStr += "\n+++ VS_BUILD_TARGETS: " + getMapOrListParam(VS_BUILD_TARGETS)
		}		
		if(printAll || params == "BUILD") {
			paramStr += "\n--------- BUILD params: ---------"
			paramStr += "\n+++ BUILD_TARGET: " + getStringParam(BUILD_TARGET)
			paramStr += "\n+++ BUILD_CONFIG: " + getStringParam(BUILD_CONFIG)
			paramStr += "\n+++ BUILD_PLATFORM: " + getStringParam(BUILD_PLATFORM)
			paramStr += "\n+++ BUILD_PLATFORM_PATH: " + getStringParam(BUILD_PLATFORM_PATH)
			paramStr += "\n+++ BUILD_PLATFORM_CONFIG: " + getStringParam(BUILD_PLATFORM_CONFIG)
		}
		if(printAll || params == "SCRIPTS") {
			paramStr += "\n--------- BUILD SCRIPT params: ---------"
			paramStr += "\n+++ SCRIPTS_PREBUILD: " + getMapOrListParam(SCRIPTS_PREBUILD)
			paramStr += "\n+++ SCRIPTS_POSTBUILD: " + getMapOrListParam(SCRIPTS_POSTBUILD)
		}
		if(printAll || params == "TH") {
			paramStr += "\n--------- TEST HARNESS params: ---------"
			paramStr += "\n+++ TESTHARNESS_SETS: " + getMapOrListParam(TESTHARNESS_SETS)
			paramStr += "\n+++ TESTHARNESS_PARAMS: " + getMapOrListParam(TESTHARNESS_PARAMS)
		}
		if(printAll || params == "SLACK") {
			paramStr += "\n--------- SLACK params: ---------"
			paramStr += "\n+++ SLACK_DO_REPORTING: " + getBoolParam(SLACK_DO_REPORTING)
			paramStr += "\n+++ SLACK_PARAMS: " + getMapOrListParam(SLACK_PARAMS)
		}
		if(printAll || params == "MISC") {
			paramStr += "\n--------- MISC params: ---------"
			paramStr += "\n+++ GLOBALS_DETAILED_PRINTS: " + getBoolParam(GLOBALS_DETAILED_PRINTS)
		}

		paramStr += "\n-------------------------------------------------"
		gpEcho(paramStr) 
	}
}
