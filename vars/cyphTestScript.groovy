#!/usr/bin/env groovy

import static athens.utils.GlobalParams.*
import static athens.utils.UtilityFunctions.*


// NOTE: CURRENTLY AN EXACT DUPLICATE OF checkAndInitGlobalParams.groovy

def call(Map params) {

	echo ">>> Testing for mandatory parameters and initialising globals"

	SetScriptForGlobalParams(this)
	
	
		def p4workspace = "EXT_FE_Jesus-desktop_Jenkins"
		echo ">>> [Perforce] Syncing Workspace '${p4workspace}'"

		withCredentials([usernamePassword(usernameVariable: 'PERFORCE_USERNAME', passwordVariable: 'PERFORCE_PASSWORD', credentialsId: 'AthensPerforceAccount')])
		{    	        
			p4sync(	
				charset: 'none', 
				credential: 'AthensPerforceAccount', 
				workspace: staticSpec(charset: 'none', name: p4workspace, pinHost: false),
				populate: 
					autoClean(
						force: false, 
						have: true, 
						modtime: false, 
						parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], 
						pin: '', 
						quiet: false, 
						revert: false), 
				)
		}
	
	
	// Make sure the Jenkinsfile has all of the parameters that are mandatory (this now lives in UtilityFunctions.groovy)
//	TestForMandatoryParams(params)

	// Test whether the pipeline needs the sync/build steps (this also now lives in UtilityFunctions.groovy)
//	PIPELINE_REQUIRES_BUILD_STEPS = PipelineRequiresBuild(params)	// CYPHTODO: FIGURE OUT WHY THIS ASSIGNMENT CAUSES BIZARRE ERRORS!!!

//	boolean requiresBuild = PipelineRequiresBuild(params)
//	SetPipelineRequiresBuild(requiresBuild)

	SetPipelineRequiresBuild(true)
//	PIPELINE_REQUIRES_BUILD_STEPS = false	
//	echo ">>> PIPELINE_REQUIRES_BUILD_STEPS: " + PIPELINE_REQUIRES_BUILD_STEPS

	def emailaddress = P4Helpers.GetEmailFromUserID("mhughes")
	echo ">>> Email address for 'mhughes' on P4 is: " + emailaddress 


	// Initialise the global params (this script lives in GlobalParams.groovy)
	// InitAndSetGlobalParams(params)

	// CYPHTODO: TEMP FOR DEBUGGING PURPOSES
	// PrintGlobalParams()

	echo ">>> Finished testing for mandatory parameters and initialising globals"
}
