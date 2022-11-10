#!/usr/bin/env groovy

import groovy.io.*
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils

import groovy.transform.Field

// This library function is a 'manager' for determining which test harness test sets should be run when executing
// a specific pipeline.  It looks for a list of test sets in the params Map, and if one is found, attempts to
// find the test set definition in the library of test sets (which has previously been loaded from XML), and if
// a definition is found, runs those tests in sequence.


// Globals to store script test harness control params for easy access, so we don't have to 
// pass the 'params' map through to every single utility function
@Field abortOnError = false
@Field abortOnTestFail = false
@Field showDebugPrints = false

// Globals to store file- and path-related data, so we only have to modify these in one place if every necessary
// NOTE: The Test Harness scripts and test set xmls are now synced to the Athens Scripts Root (from the Jenkins repository)
@Field testHarnessRootFolder = "${ATHENS_SCRIPTS_ROOT}\\TestHarness" 
@Field testSetDefintionsPath = "${testHarnessRootFolder}\\TestSetDefinitions"
@Field testOuputPath = "${testHarnessRootFolder}\\TestOutputFiles"
@Field testFileExtension = "fts"   // NOTE: If we end up having more than one acceptable extension, turn this into a list

// We store each test set definition in a global list after reading it in from the XML file,
// so we can easily get each test set's list of tests when we need to execute it 
@Field testHarnessTestSetsList = []

/*
	NOTE: When the test set xmls are read, the data will be put into the above list, which
	will have the following format:

	def testHarnessTestSetsList = [
				[ 
					testSetName: 'TestSet1',
					testScripts:  	[
										'TH script 1',
										'TH script 2',
										'TH script 3',
									],
				],
				[ 
					testSetName: 'TestSet2',
					testScripts:  	[
										'TH script 4',
										'TH script 5',
									],
				],				
			]
*/

// List of Maps
@Field gTestResultList = []

/*
// Map format:
[
	testSet: 'Test Set Name',
	test: 'Test Name',
	retVal: num
	errorOutput: 'String'
	outputFile: 'File Path'	
]
*/


def DoError(def errString) {
	if(abortOnError)
		error(">>> ERROR: ${errString}\n>>> ERROR NOTE: Set 'AbortOnTestSetFormatError' param to 'false' (or omit entirely) to print error and allow execution to continue instead of aborting.")
	else
		echo ">>> NON-FATAL ERROR: ${errString}\n>>> ERROR NOTE: Set 'AbortOnTestSetFormatError' param to 'true' to abort when this error is encountered."
}

def DebugPrint(def str) {
	echo ">>> TH_DEBUG: ${str}" 
}

def OptDebugPrint(def str) {
	if(showDebugPrints) {
		echo ">>> TH_DEBUG: ${str}" 
	}
}


def isNullOrEmpty(def str) {
	return ((str == null) || str.allWhitespace)
}


@NonCPS
def getFileListFromDirectory(def dir, def list){
	dir.eachFileRecurse (FileType.FILES) { file ->
		file = file.toString()
		list << file
	}
}


def FindTestDefinitionList(String testName)
{
	for (int i=0; i < testHarnessTestSetsList.size(); i++) {
	
		def testSet = testHarnessTestSetsList[i]

		if(testSet.containsKey("testSetName")) {	
			def name = testSet.get("testSetName")
			if(name == testName) {
						
				if(testSet.containsKey("testScripts")) {
					return testSet.get("testScripts")
				}
				else {
					DoError("Test Set '${name}' definition does NOT contain key 'testScripts'")					
				}
			}
		}
		else {
			DoError("Test Set '${name}' definition does NOT contain key 'testSetName'")			
		}
	}
	
	DoError("Could NOT find Test Set with name: ${testName} in list of Test Set definitions: \n>>> ${testHarnessTestSetsList}")

	return null
}


def printTestResultsToConsoleLog(String testName, String outputFilePath)
{
	def testOuput = readFile(file: outputFilePath)
	echo ">>> ------------- Test Harness test '${testName}' output: -------------"
	echo testOuput
	echo ">>> -------------------------------------------------------------------"
}


@NonCPS
def runCommand(String cmd)
{	
	def resultInfo = [:]
	resultInfo.retVal = -1

	try {
		process_output = new StringBuffer();
		process_errors = new StringBuffer();

		def proc = cmd.execute()

//		proc.waitForProcessOutput(process_output, process_errors)	// CYPH - TEMP FOR TESTING
		
		proc.consumeProcessOutput(process_output, process_errors)
		proc.waitForOrKill(8 * 60 * 1000)	// Terminate if command hasn't finished after 8 minutes
	
		echo ">>> Command Output: " + process_output.toString()
		
		resultInfo.retVal = proc.exitValue()

		echo ">>> Command Return Val: " + resultInfo.retVal
		
		if(resultInfo.retVal != 0) {
			echo ">>> Command Errors: " + process_errors.toString()
			resultInfo.errorOutput = process_errors.toString()
		}
		
		return resultInfo
	}
	catch (IOException e) {
		
		if(abortOnError)
			error(">>> ERROR: Test Harness command execution timed out and the process was killed before completing")
		else
			echo ">>> NON-FATAL ERROR: Test Harness command execution timed out and the process was killed before completing"
	}

	return resultInfo
}


def RunTest(String platform, String testScript)
{
	DebugPrint("Running Test Harness test: '${testScript}'")

	if(platform == 'Steam') {
		
		String thScript = "${testHarnessRootFolder}\\${testScript}.${testFileExtension}"
		String thOutputFile = "${testOuputPath}\\${testScript}_Output.txt"		// CYPHTODO: THIS IS TEMPORARY, FOR TESTING, STILL GOTTA WORK OUT WHAT TO DO WITH OUTPUT
		
		String testHarnessCommand = """${ATHENS_DEVELOPMENT_ROOT}\\athens.exe -nobugsplat -suppressmemoryleakpopup THScript=\"${thScript}\" THReport=\"${thOutputFile}\""""		
		OptDebugPrint("Executing command: '${testHarnessCommand}'")

		// Run the Test Harness test with the generated command
		def resultInfo = runCommand("${testHarnessCommand}")
		resultInfo.test = testScript
		resultInfo.outputFile = thOutputFile
		
		OptDebugPrint("Command complete! [resultInfo: ${resultInfo}]")
	
		// CYPHTODO: Test whether output file is where we expect it to be
		
		// CYPHTODO: When the above test is functional, print a "Found output file!" message if found,
		// and only print the directory contents on 'file not found'
		if(showDebugPrints) {

			// See what files are in the output directory:					
			OptDebugPrint("Getting updated directory contents for: '${testOuputPath}'")
			def fileList = []
			def filesInDir = new File("${testOuputPath}")
			getFileListFromDirectory(filesInDir,fileList)
			OptDebugPrint("Found files: \n${fileList}")
		}


		// Print the contents of the output file to the Jenkins console log
		printTestResultsToConsoleLog(testScript, thOutputFile)
		
		DebugPrint("Finished running Test Harness test: '${testScript}'")
		
		return resultInfo		
	}
	else if (platform == 'MSIXVC')
	{	
		DebugPrint("MSIXVC is not currently set up to run Test Harness tests")				
	}	
	
	def resultInfo = [:]
	resultInfo.retVal = -1

	return resultInfo
}


def RunTestSet(String platform, String testSet) 
{
	DebugPrint("Running Test Harness test set: '${testSet}' on Platform: '${platform}'")
		
	def testList = FindTestDefinitionList(testSet)
		
	if(testList != null) {
		for (int i=0; i < testList.size(); i++) {
			def resultInfo = RunTest(platform, "${testList[i]}")			
			resultInfo.testSet = testSet

			// If the test failed, add the test results to the 'failed test' list and check to see whether we should abort
//			if(resultInfo.retVal != 0) {	// TEMP DISABLING FOR TESTING
				OptDebugPrint("Returned test results struct: ${resultInfo}")
			
				gTestResultList.add(resultInfo)
				
				if(abortOnTestFail)
					break;				
//			}			
		}
		
		DebugPrint("Finished Running Test Harness test set '${testSet}' on platform: '${platform}'")
	}
	else {	
		DoError("Could NOT find Test Set with name: ${testSet} in list of Test Set definitions")
	}
}


def RunTestSets(String platform, List runTestSets) 
{
	DebugPrint("Running Test Harness test sets: ${runTestSets}")

	for (int i=0; i < runTestSets.size(); i++) {
		RunTestSet(platform, runTestSets[i])
	}

	echo ">>> Test Output Result List:\n>>> " +	gTestResultList

	DebugPrint("Finished Running Test Harness test sets")
}


def RunTestsOnBuild(def platform, List runTestSets)
{
	// Run the Test Harness test sets on the platform/config specified

	// CYPHTODO: Code currently errors if we try to run Test Harness for MSIXVC, even though
	// we abort before actually running the test, so don't even start the sequence right now.
	// This check can be removed once I've dealt with the issue at a later date.
	if (platform != 'Steam') {
		return;
	}

	// Sanity check
	if((platform != 'Steam') && (platform != 'MSIXVC'))
	{
		DoError("Unknown build platform: '${platform}'")
		return;
	}

	RunTestSets(platform, runTestSets)
}


def loadAndParseXMLFile(String filePath)
{
	OptDebugPrint("Loading Test Harness Test Set data from file: '${filePath}'")

	def xmlFromFile = readFile(file: filePath)
	def testSet = new XmlParser().parseText(xmlFromFile)

	def setName = FilenameUtils.getBaseName(filePath);
	
	OptDebugPrint("Loaded content from test set '${setName}':\n${testSet}")
		
	// This map will contain all of the tests once they're read in from their respective XMLs	
	Map testSetMap = [:];
	testSetMap.testSetName = setName
	
	// Create an empty list to store the list of tests in this test set 
	testSetMap.put("testScripts", [])
	
	// Go through each of the sub-lists of tests (the XML may contain multiple sub-lists of files from different subdirectories) 
	for(testSubList in testSet.testList){
	
		// Add each test to our master list of tests, adding the subdirectory to the path if needed
		for(test in testSubList.test) {		

			String testFile = test.text()
			String subDir = testSubList['@subDir']
			String testFileName = FilenameUtils.getBaseName(testFile)
			String testFileExt = FilenameUtils.getExtension(testFile)
			
			// Check whether file extension is acceptable (no extension is acceptable, but if there _is_ a file extension, it must be .fts)
			if((isNullOrEmpty(testFileExt) || (testFileExt == testFileExtension)) == false) {			
				String err = "'.${testFileExt}' is not an acceptable file extension for a test file, must be '.${testFileExtension}' (or omitted from filename entirely)" + 
					"\n>>> ERROR FOUND IN: '${filePath}'" + 
					"\n>>> TEST FILE LISTED: '${subDir}\\${testFile}'"			
				DoError(err)
			}
							
			String testFilePath
			
			if(isNullOrEmpty(subDir))
				testFilePath = testFileName
			else 
				testFilePath = subDir + "\\" + testFileName

			testSetMap.testScripts.add(testFilePath)		
		}		
	}
	
	OptDebugPrint("Parsed loaded data from test set '${setName}' into data struct:\n${testSetMap}")		
	
	OptDebugPrint("Finished Loading '${setName}' XML test set data")
	
	return testSetMap
}


def loadTestSetDefinitions(List requiredTestSets)
{
	OptDebugPrint("Loading Test Harness test set XMLs from dir: ${testSetDefintionsPath}")

	def fileList = []
	def filesInDir = new File("${testSetDefintionsPath}")
	getFileListFromDirectory(filesInDir,fileList)

	OptDebugPrint("Found test set files: \n${fileList}")

	OptDebugPrint("Matching files against list of requried test sets (based on pipeline params)")
	
	def requiredFileList = []

	// Go through the required test sets (based on pipeline params) and and look for an XML definition file for each
	for (testSet in requiredTestSets) {

		// Note: We want to match the filename exactly in case some test set names are substrings of others, so we 
		// add the preceding \ and following . in the search string to ensure only exact matches will be found
		def testSetMatchString = "\\${testSet}."

		if(fileList.any { it.contains(testSetMatchString) }) 
		{				
			// If a definition file was found for this required test set, add it to the list
			def foundFileName = fileList.find { it.contains(testSetMatchString) }
			OptDebugPrint("XML for required test set '${testSet}' found: '${foundFileName}'")
			requiredFileList.add(foundFileName)						
		}
		else {
			DoError("No XML definition file found for test set '${testSet}'")
		}		
	}
	
	OptDebugPrint("Required test set XML list: \n${requiredFileList}")

	// Load and parse each of the required XMLs, and add the data in our global test set definition list
	for (filename in requiredFileList) {	
		def testSet = loadAndParseXMLFile(filename)
		testHarnessTestSetsList.add(testSet)
	}

	OptDebugPrint("Complete list of test sets: \n${testHarnessTestSetsList}")

	OptDebugPrint("Finished Loading Test Harness Test Set Definitions")
}


// This function is now called from 'runPostBuildScripts.groovy', rather than directly from the pipeline
def call(def platform, def config, def runTestHarnessTestSets, def testHarnessParams) {

	DebugPrint("Test Harness Framework: Preparing to load and run tests")

	if(!platform) {
		DebugPrint("No 'platform' parameter specified, cannot run tests")
		return
	}

	if(!config) {
		DebugPrint("No 'config' parameter specified, cannot run tests")
		return
	}

	if(!runTestHarnessTestSets) {
		DebugPrint("No 'runTestHarnessTestSets' parameter specified, cannot run tests")
		return
	}
	
	if(runTestHarnessTestSets.size() < 1) {
		DebugPrint("No Test Sets defined in 'runTestHarnessTestSets', skipping this step")
		return
	}

	if((config != 'Final') && (config != 'Playtest') && (config != 'Debug'))
	{
		DoError("Unknown build config: '${config}'")
		return
	}
	
	echo "deleting c drive cache."
	bat'''
		if exist "C:\\ProgramData\\Age of Mythology DE\\" rmdir "C:\\ProgramData\\Age of Mythology DE\\" /q /s
	'''

	if(testHarnessParams != null) {	
		if(testHarnessParams.AbortOnTestSetFormatError != null) {		
			abortOnError = testHarnessParams.get("AbortOnTestSetFormatError")				
		}
		if(testHarnessParams.AbortOnFirstTestFail != null) {		
			abortOnTestFail = testHarnessParams.get("AbortOnFirstTestFail")				
		}
		if(testHarnessParams.ShowTestHarnessDebugPrints != null) {		
			showDebugPrints = testHarnessParams.get("ShowTestHarnessDebugPrints")				
		}		
	}
	
	OptDebugPrint("Test Harness Params: 'AbortOnTestSetFormatError': [${abortOnError}]  'AbortOnFirstTestFail': [${abortOnTestFail}]  'ShowTestHarnessDebugPrints': [${showDebugPrints}]")	
	
	OptDebugPrint("Copying 'athens.exe' and 'athens.pdb' to the Development root so that the .exe can find the project .dlls when Test Harness runs it")

	// CYPHTODO: ERROR CHECKING - PUT IN A CHECK HERE TO DETECT WHETHER THIS EXE EXISTS 
	// (AND PREFERABLY WAS BUILT VERY RECENTLY, IE, BY THIS PIPELINE) AND IF NOT, ERROR AND ABORT
	
	// To run athens.exe we need to move it from the 'intermediate' directory where it was built (in the previous step)
	// to the root directory of the project, so that when it runs it can find the relevant .dlls
	withEnv(["CONFIG=${config}"])
	{
		bat '''
			xcopy  /Y "%ATHENS_DEVELOPMENT_ROOT%\\intermediate\\Steam\\age3\\%CONFIG%\\athens.exe" "%ATHENS_DEVELOPMENT_ROOT%"
			xcopy  /Y "%ATHENS_DEVELOPMENT_ROOT%\\intermediate\\Steam\\age3\\%CONFIG%\\athens.pdb" "%ATHENS_DEVELOPMENT_ROOT%"
		'''
	}
	
	// Clear out the 'Output' directory so that test run output files don't just accumulate
	// CYPHTODO: Remove this code when we implement the next stage of output management
	def fileList = []
	def filesInDir = new File("${testOuputPath}")
	getFileListFromDirectory(filesInDir,fileList)

	echo ">>> Contents of output directory BEFORE: \n${fileList}"	// TEMP FOR TESTING

	if(fileList.size() > 0)	{
		File folder = new File("${testOuputPath}")
		FileUtils.cleanDirectory(folder)

		// TEMP FOR TESTING
		filesInDir = []
		fileList = []
		filesInDir = new File("${testOuputPath}")
		getFileListFromDirectory(filesInDir,fileList)
		echo ">>> Contents of output directory AFTER: \n${fileList}"
	}	

	DebugPrint("Test Harness test sets to run: ${runTestHarnessTestSets}")

	// Load test set definition xmls into global variable 'testHarnessTestSetsList'
	loadTestSetDefinitions(runTestHarnessTestSets)	

	DebugPrint("Running Test Harness tests on platform '${platform}' and config '${config}'")

	// Run our tests on the platform specified
	RunTestsOnBuild(platform, runTestHarnessTestSets)
	
	DebugPrint("Test Harness Framework: All test sets are now complete")
}




