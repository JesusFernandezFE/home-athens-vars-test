#!/usr/bin/env groovy

import static athens.utils.GlobalParams.*
import static athens.utils.UtilityFunctions.*


def call(Map params) {

	echo ">>> Testing for mandatory parameters and initialising globals"

	SetScriptForGlobalParams(this)
	
	// Make sure the Jenkinsfile has all of the parameters that are mandatory (this now lives in UtilityFunctions.groovy)
	TestForMandatoryParams(params)

	// Test whether the pipeline needs the sync/build steps (this also now lives in UtilityFunctions.groovy)
//	PIPELINE_REQUIRES_BUILD_STEPS = PipelineRequiresBuild(params)	// CYPHTODO: FIGURE OUT WHY THIS ASSIGNMENT CAUSES BIZARRE ERRORS!!!
	boolean requiresBuild = PipelineRequiresBuild(params)	
	SetPipelineRequiresBuild(requiresBuild)
	
	// Initialise the global params (this script lives in GlobalParams.groovy)
	InitAndSetGlobalParams(params)

	// CYPHTODO: TEMP FOR DEBUGGING PURPOSES
	PrintGlobalParams()

	echo ">>> Finished testing for mandatory parameters and initialising globals"
}
