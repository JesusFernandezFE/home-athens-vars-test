#!/usr/bin/env groovy

import static athens.utils.GlobalParams.*
import static athens.utils.UtilityFunctions.*


// Utility functions
def listNotEmpty(Map params, String key)
{
	if(params.containsKey(key) == true) {
	
		def param = params.get(key) 
		
		if(param.size() > 0)
		{
			echo ">>> notEmpty() - param [${key}] is not empty!"
			return true;		
		}
	}
	
	echo ">>> notEmpty() - param [${key}] was not found or was empty"
	return false;
}

def paramExistsAndIsTrue(Map params, String key)
{
	if(params.containsKey(key) == true) {
	
		echo ">>> paramExistsAndIsTrue() - param [${key}] exists!"

		def param = params.get(key) 
		if(param instanceof Boolean) {
			echo ">>> paramExistsAndIsTrue() - param [${key}] is a boolean!"
			return param;
		}
		else {
			echo "ERROR: Param '${key}' is not of type Boolean, and should not be sent to function 'paramExistsAndIsTrue()'"		
		}
	}
	
	echo ">>> paramExistsAndIsTrue() - param [${key}] was not found in: ${params} "
	return false;
}

// Perforce functionality
def syncPerforce(String p4workspace) {

	echo ">>> [Perforce] Syncing Workspace '${p4workspace}'"
 	        
       	p4sync(	
       		charset: 'none', 
       		credential: 'P4-Athens', 
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

def switchWorkspaceStream(String workspace, String stream) {

	echo ">>> [Perforce] Switching Workspace '${workspace}' to Stream '${stream}'"

	String streamPath = "//stream/Athens/${stream}" 

	withEnv(["WORKSPACE=${workspace}", "STREAM_PATH=${streamPath}", "PROJECT_SCRIPTS=${PROJECT_SCRIPT_ROOT}", "JENKINS_SCRIPTS=${JENKINS_SCRIPT_ROOT}"]) {
		bat"""
			cd %PROJECT_SCRIPTS%
			%JENKINS_SCRIPTS%\\python\\python.exe -c \"import perforce_helpers; perforce_helpers.switchWorkspaceStream('%STREAM_PATH%', '%WORKSPACE%')\"
		"""
	}
}


def call(Map params) {
	
    echo ">>> Syncing Project and Scripts repository from Perforce"

	PrintGlobalParams("P4")

//	String streamName = params.get("stream")
//	String workSpaceName = params.get("workspace")

	// If we're using the 'shared' workspace, switch the stream associated with the shared workspace
	if(P4_WORKSPACE.contains('Development') || P4_WORKSPACE.contains('Shared') || P4_WORKSPACE.contains('Stable')) {
		switchWorkspaceStream(P4_WORKSPACE, P4_STREAM)	
	}

	// Sync the project to the requested workspace
	echo ">>> Syncing '${P4_STREAM}' to '${P4_WORKSPACE}'!"
	syncPerforce(P4_WORKSPACE)

	// Sync the Jenkins script repo to the scripts workspace (if needed)		
	if(listNotEmpty(params, "runPreBuildScripts") ||
		listNotEmpty(params, "runPostBuildScripts") ||
		listNotEmpty(params, "runTestHarnessTestSets") )	
	{
		echo ">>> Syncing 'Jenkins' to '${ATHENS_SCRIPTS_WORKSPACE}'!"
		syncPerforce("${ATHENS_SCRIPTS_WORKSPACE}")		
	}
	
    echo ">>> Finished Syncing Project and Scripts"
}




