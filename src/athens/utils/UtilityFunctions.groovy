#!/usr/bin/env groovy
package athens.utils

class UtilityFunctions {

	static def uEcho(String echoStr) {
		if(GlobalParams.gp_script) {
			GlobalParams.gp_script.echo(echoStr)
		}
	}

	static def uError(String echoStr) {
		if(GlobalParams.gp_script) {
			GlobalParams.gp_script.echo("+++ UTILS ERROR: ${echoStr}")
		}
	}

	static def uErrorandAbort(String echoStr) {
		if(GlobalParams.gp_script) {
			GlobalParams.gp_script.error("+++ UTILS ERROR: ${echoStr} [ABORTING PIPELINE]")
		}
	}

	// Note: If a param is flagged as a 'baseParam', it MUST be included in the root of the 'params'
	// map.  If it is flagged as a 'baseOrTargetsParam', it may be included EITHER in the root of
	// the 'params' map, OR in the 'targets' sub-list contained within the 'params' map.
	static def mandatoryParams = [
			workspace: 'baseParam',
			stream: 'baseParam',
			target: 'baseOrTargetsParam',
			configs: 'baseOrTargetsParam',
			platforms: 'baseOrTargetsParam'
		]

	static def TestForMandatoryParams(Map params) {
		uEcho("+++ Testing for mandatory Jenkinsfile parameters")		
		def allParamsFound = true
		def buildTargets = [:]

		static def TestForParamsOfType = { paramType, paramMap ->
			// Loop through the mandatory params list
			mandatoryParams.each { mandatoryParam ->
				// Test whether the param is one that we need to check for at this level ('base-of-map' vs 'base-of-map or targets sub-map')
				if(mandatoryParam.value == paramType) 
				{
					// And if so, see if we can find a matching key
					def key = mandatoryParam.key
					if(paramMap.containsKey(key) == false) {	
						uError("Mandatory param key '${key}' not found in '${paramType}' map")
						allParamsFound = false
					}
				}
			}
		}

		// Test for all the base-level parameters
		TestForParamsOfType('baseParam', params)

		// The 'build target' params may be included at the base level of the params map (if there is only a single build target), 
		// or in an array of build targets called, unsurprisingly, 'targets'.  If the 'build target' params are in the base level, 
		// temporarily add them to an array so that we can assess all the build targets in the same way.
		if(params.target != null) {			
			buildTargets =  [ [ 
								target: params.target,
								configs: params.configs,
								platforms: params.platforms
							] ]
		}
		else if(params.targets != null) {
			buildTargets = params.targets 
		}
		else {
			uError("The 'params' map must contain either a 'target' or 'targets' param, but neither were found")			
		}

		// For each build target in the 'build target' list...
		for(int b = 0; b<buildTargets.size; b++) {
			// Check for each of the mandatory params	
			TestForParamsOfType('baseOrTargetsParam', buildTargets[b])
		}
		
		if(allParamsFound != true) {
			uErrorandAbort("Missing at least one mandatory key in the 'params' map for this pipeline")
		}
		
		uEcho("+++ All mandatory Jenkinsfile parameters were found!")
	}

	// This function tests to see whether the 'configs' and 'platforms' parameters of the 'params' block defined 
	// in the Jenkinsfile are defined but are empty.  This indicates that a build step is NOT required.
	static def PipelineRequiresBuild(Map params) {
		uEcho("+++ Testing for whether pipeline requires build")
			
		if(params.targets != null) {
			return true
		}

		def noConfigs = (params.configs ? false : true)
		def noPlatforms = (params.platforms ? false : true)

		if((noConfigs && !noPlatforms) || (!noConfigs && noPlatforms)) {
			uError("PipelineRequiresBuild: 'configs' and 'platforms' params mismatch - both must be NULL/EMPTY or both must be NOT NULL/EMPTY!")
		}

		uEcho("+++ Pipeline Requires Build: " + (!noConfigs && !noPlatforms))
		
		return (!noConfigs && !noPlatforms)
	}

	// This nested class basically acts like a namespace, and is used to group
	// utility functions with related funcitonality
	static class P4Helpers {

		// This function can be called directly, or alias functions can be created, see below
		static def CallPerforceHelper(String helperFn, String param) {

			if(!GlobalParams.gp_script) {
				uError("CallPerforceHelper() - GlobalParams.gp_script is unset")
				return ""
			}

			def retVar = ""

			String JENKINS_SR = GlobalParams.JENKINS_SCRIPT_ROOT
			String PROJECT_SR = GlobalParams.PROJECT_SCRIPT_ROOT

			boolean strip = true
			String stripStr = ".strip()"

			// Test for any functions which return an int/float/etc rather than a string
			if(helperFn == "getCurrentChangelist") {
				strip = false;
				stripStr = ""
			}

			String pythonCall = "import perforce_helpers; tempVar = perforce_helpers.${helperFn}('${param}'); print(tempVar${stripStr})"
			uEcho("+++ CallPerforceHelper() - pythonCall: ${pythonCall}")

			GlobalParams.gp_script.withEnv(["PROJECT_SCRIPTS=${PROJECT_SR}", "JENKINS_SCRIPTS=${JENKINS_SR}", "PYTHON_CALL=${pythonCall}"])
			{
				def batScript = """@echo off
						   cd %PROJECT_SCRIPTS%
						   %JENKINS_SCRIPTS%\\python\\python.exe -c \"%PYTHON_CALL%\""""

				if(GlobalParams.gp_script) {
					if(strip)
						retVar = GlobalParams.gp_script.bat( label: '', returnStdout: true, script: "${batScript}" ).trim()
					else
						retVar = GlobalParams.gp_script.bat( label: '', returnStdout: true, script: "${batScript}" )
				}
			}
			
			return retVar
		}		
		
		static def GetEmailFromUserID(String name) {
			def email = CallPerforceHelper("getEmailFromUserID", name)
			uEcho("+++ GetEmailFromUserID() - email: ${email}")
			return email
		}

		static def GetWorkspaceRoot(String workspace) {
			
			if(!GlobalParams.gp_script) {
				uError("CallPerforceHelper() - GlobalParams.gp_script is unset")
				return ""
			}

			String workspaceRoot = ""
			String P4_WS = GlobalParams.P4_WORKSPACE

			// There is a problem getting the workspace root via the perforce_helpers.py - we can't _call_
			// perforce_helpers.py without knowing the workspace root, so there is a circular dependency
			// we can't resolve here.  This is a hack to get around it - we call the p4 command to get
			// the workspace root directly via a batch script.
				
			GlobalParams.gp_script.withEnv(["P4_WORKSPACE=${P4_WS}"])
			{
				def batScript = """@echo off
									p4 -c %P4_WORKSPACE% -F \"%%clientRoot%%\" -ztag info"""

				if(GlobalParams.gp_script) {
					workspaceRoot = GlobalParams.gp_script.bat( label: '', returnStdout: true, script: "${batScript}" ).trim()
				}
			}

			uEcho("+++ GetWorkspaceRoot() - workspaceRoot: ${workspaceRoot}")
			return workspaceRoot
		}

		static def GetCurrentCL(String workspace) {
			def currentCL = CallPerforceHelper("getCurrentChangelist", workspace)
			uEcho("+++ GetCurrentCL() - currentCL: ${currentCL}")
			return currentCL
		}
	}

	static def ParamExistsAndIsTrue(Map params, String key)
	{
		if(params.containsKey(key) == true) {

			// uEcho("+++ paramExistsAndIsTrue() - param [${key}] exists!")

			def param = params.get(key) 
			if(param instanceof Boolean) {
				// uEcho("+++ paramExistsAndIsTrue() - param [${key}] is a boolean!")
				return param;
			}
			else {
				uError("Param '${key}' is not of type Boolean, and should not be sent to function 'paramExistsAndIsTrue()'")
			}
		}

		// uEcho("+++ paramExistsAndIsTrue() - param [${key}] was not found in: ${params}")
		return false;
	}
	
}
