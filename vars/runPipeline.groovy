#!/usr/bin/env groovy

import static athens.utils.GlobalParams.*
import static athens.utils.UtilityFunctions.*

// This function runs the main pipeline, stepping through each stage and executing any functionality 
// determined by the pipeline params (which are set in the relevant Jenkinsfile).  The params map object 
// is passed through to the library functions called from the main pipeline, which lets each function 
// determine exactly what functionality to execute.  Note that this pipeline sequence is common to ALL 
// of the Athens Jenkins projects - individual steps can be excluded if not needed by a particular 
// pipeline by setting relevant params. 


def call(Map params) {

	pipeline {
		agent
		{
			node{
				label 'talavera'
      			customWorkspace 'E:\\Athens'
			} 
		}

		stages {
			stage('Start Pipeline') {
				steps {
 					echo 'Starting Pipeline!'
				}
			}

			stage('Init Global Pipeline Params') {
				steps {
					checkAndInitGlobalParams(params)
				}
			}

			stage('P4 Sync') {
				when { expression { return PIPELINE_REQUIRES_BUILD_STEPS } }
				steps {	
					syncPerforceStream(params);
				}
			}

			stage('Run Pre-Build Scripts') {
				steps {
					runPreBuildScripts(params);
				}
			}

			// stage('Generate VS Projects') {
			// 	when { expression { return PIPELINE_REQUIRES_BUILD_STEPS } }
			// 	steps {
			// 		generateVSProjects()
			// 	}
			// }

			// stage('Build VS Projects') {
			// 	when { expression { return PIPELINE_REQUIRES_BUILD_STEPS } }
			// 	steps {
			// 		buildVSProjects(params)						
			// 	}
			// }

			stage('Run Post-Build Scripts')
			{
				steps{
					script {
						runPostBuildScripts(params)
					}
				}
			}

			stage('Finished Pipeline') {
				steps {
					echo 'Finished Pipeline!'
				}
			}
		}
		
	}
}




