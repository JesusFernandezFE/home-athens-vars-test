#!/usr/bin/env groovy

// This library function is a 'manager' for determining whether the stability test should be run when executing
// a specific pipeline.  It looks for specific options in the params Map, and if they are present, tests for 
// true/false status to determine whether the associated script block should be executed.  (If the option is 
// not contained in the params set, this automatically counts as a 'false'.)


def DoStabilityTest(String scriptParam, String platformPath, String config)
{
	echo ">>> Running stability test for Platform: '${platformPath}' Config: '${config}'"

	if(platformPath == 'Steam')
	{
		withEnv(["PLATFORM_PATH=${platformPath}", "CONFIG=${config}"]) {

			if(scriptParam == "runStabilityTest") {
				echo ">>> Running stability test with 'intermediate\\$PLATFORM_PATH\\age3\\$CONFIG\\athens.exe'"
				
				bat '''cd %ATHENS_DEVELOPMENT_ROOT% 
				%ATHENS_DEVELOPMENT_ROOT%\\intermediate\\%PLATFORM_PATH%\\age3\\%CONFIG%\\athens.exe -test=stability
				echo Stability Test complete, RetVal: %ERRORLEVEL%
				'''
			}
			else if(scriptParam == "stabilityTestViaBat") {
				echo ">>> Running stability test with 'Build\\athens.exe' via the 'Athens_Develop.bat' script"

				if(config != 'Playtest') {
					echo ">>> ERROR: The stability test can only be run through 'Athens_Development.bat' in 'Playtest' config"
					return
				}

				// CYPHTODO: THE RETURN VAL BELOW IS PROBABLY GOING TO PRINT OUT THE RETURN VAL OF THE BATCH
				// FILE - NEED TO MAKE SURE THIS IS PASSING OUT THE RETURN VAL OF THE EXE CALL	
				bat '''cd %ATHENS_DEVELOPMENT_ROOT% 
				%ATHENS_DEVELOPMENT_ROOT%\\Athens_Development.bat -test=stability
				echo Stability Test (via 'Athens_Development.bat') complete, RetVal: %ERRORLEVEL%
				'''
			}
		}
	}
	else if (platformPath == 'Gaming.Desktop')
	{	
		//runMSIXVC smokes.
		echo ">>> NOTE: MSIXVC stability test functionality not yet implemented"
		
		// Note: The stability test cannot currently be run through 'Athens_Development.bat' for MSIXVC
		// builds, as the .bat file is hard-coded to use the steam .exes copied into the build directory.
		// If we want to run the MSIXVC stability test this way, this will need to be dealt with somehow.
	}
}

// The stability test can only be run on 'Playtest' builds, so this functionality 
// has been simplified to take this into account.  Also cleaned up unused variables.

def call(String scriptParam, String platform, String config) {

	echo ">>> Running Stability Tests with param '${scriptParam}', platform '${platform}', config '${config}'" 

	String platformPath;

	if(platform == 'Steam')
	{
		platformPath = 'Steam'
	}
	else if(platform == 'MSIXVC')
	{
		platformPath = 'Gaming.Desktop'
	}
	else
	{
		echo ">>> Run Stability Tests: Unknown build platform: ${platform}"
		return
	}

	if((config != 'Final') && (config != 'Playtest') && (config != 'Debug'))
	{
		echo ">>> Run Stability Tests: Unknown build config: '${config}'"
		return
	}

	DoStabilityTest(scriptParam, platformPath, config);
	
	echo ">>> Finished runing Stability Tests"
}

