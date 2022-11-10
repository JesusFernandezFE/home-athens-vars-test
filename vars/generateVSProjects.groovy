#!/usr/bin/env groovy

import static athens.utils.GlobalParams.*

def call() {
    echo ">>> Generating VS Projects with 'GenerateProject.bat'"
	
	if(P4_STREAM == 'legacy') {
		echo ">>> The 'Legacy' build doesn't need a .sln generated, skipping this step"
		return
	}
	
		withEnv(["PROJ_ROOT=${PROJECT_ROOT}"]) {
			bat '''pushd %PROJ_ROOT%
				   call GenerateProject.bat
				   popd'''

    }
}
