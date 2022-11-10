#!/usr/bin/env groovy
import hudson.FilePath;
import jenkins.model.Jenkins;
import groovy.io.*
import static athens.utils.UtilityFunctions.*

// This library function is a 'manager' for determining if a slack report should be run when executing
// a specific pipeline.  It looks for specific options in the params Map, and if they are present, tests for 
// true/false status to determine whether the associated report block should be executed.  (If the option is 
// not contained in the params set, this automatically counts as a 'false'.) As well as this, it should manage
// what type of function to call, dependant on if the slack report is a fix, fail or success.

@NonCPS
def getFileListFromDirectory(def dir, def list){
	dir.eachFileRecurse (FileType.FILES) { file ->
		file = file.toString()
		list << file
	}
}


@NonCPS
def GenerateListOfNames()
{
	def changeLogSets = currentBuild.changeSets
	def arrayOfNames = new ArrayList()
	
	for (int i = 0; i < changeLogSets.size(); i++) {
		def entries = changeLogSets[i]
		def entriesItems = entries.items
		
	
		for (int j = 0; j < entries.size(); j++) {
			def entry = entries[j]

			def name = entry.author.toString()
			echo "${name}"

	    		def thisEntryHasAlreadyBeenAdded = false
			
			for(int k = 0; k < arrayOfNames.size(); k++){
				if(arrayOfNames[i] == "${name}")
				{
					echo "${name} has already been added"
					thisEntryHasAlreadyBeenAdded = true
				}

			}
			if(thisEntryHasAlreadyBeenAdded == false)
			{
				arrayOfNames.add("${name}")
			}
		}
	}
	
	return arrayOfNames

}

@NonCPS
def GenerateSlackMessageFromCls(Map params)
{
	def changeSetString = """ """ ///append to this.

	def changeLogSets = currentBuild.changeSets
	
	def arrayOfChangeNumbersAdded = new ArrayList()
	
	


	for (int i = 0; i < changeLogSets.size(); i++) {
//		def entries = changeLogSets[i].items   //   will cause serialise exception so the below instead
		def entries = changeLogSets[i]
		def entriesItems = entries.items



		for (int j = 0; j < entries.size(); j++) {
               def entry = entries[j]

		   def name = entry.author.toString()

		   //THIS CANNOT BE DONE HERE DUE TO CPS RULES.
		   //emailaddress = P4Helpers.GetEmailFromUserID(name)
		   //echo "${emailaddress}"	
	   	   //def slackUserId = slackUserIdFromEmail("emailaddress")


	
		   def thisEntryHasAlreadyBeenAdded = false	

		   for(int k = 0; k < arrayOfChangeNumbersAdded.size(); k++)
		   {
			def oldCommitId =  arrayOfChangeNumbersAdded[k]

			if(entry.commitId == oldCommitId)
			{
				thisEntryHasAlreadyBeenAdded = true
			}
		   }
		if(thisEntryHasAlreadyBeenAdded == false)
		{
			changeSetString = changeSetString + """${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg} 
"""
			echo """${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}.
"""


			arrayOfChangeNumbersAdded.add(entry.commitId)
			echo "adding ${entry.commitId} to arrayOfChangeNumbersAdded"
		}
		



	echo "third point"
               def files = new ArrayList(entry.affectedFiles)
               for (int k = 0; k < files.size(); k++) {
                   def file = files[k]
                   echo " ${file.editType.name} ${file.path}"
               }
          	}
	}

	echo changeSetString
	echo "Log Will end here before the slack call. For Full log, please visit jenkins."
	return changeSetString

}

def TagAppropriatePeople(String channelString)
{
	def arrayOfNames = new ArrayList()
	arrayOfNames = GenerateListOfNames()

	echo "size of array of names is " + arrayOfNames.size()



	def slackUserIDs = new ArrayList()
	def AddedSlackUserIDs = new ArrayList()

	for(int i = 0; i< arrayOfNames.size(); i++)
	{
		def name = arrayOfNames[i]

		def emailaddress = P4Helpers.GetEmailFromUserID(name)                 
		echo "${emailaddress}"
		if("${name}" == "buildfarm")
		{    
			//If build farm add my user id, then change the email to cyphs so the loop continues ok, but we dont attempt to find buildfarms slack account.
			def slackUserId = slackUserIdFromEmail("cdeitch@tantalus.com.au")
			slackUserIDs.add(slackUserId)
			slackUserId = slackUserIdFromEmail("mhughes@tantalus.com.au") 
			slackUserIDs.add(slackUserId)
		}
		def slackUserId = slackUserIdFromEmail("${emailaddress}")
		slackUserIDs.add(slackUserId)
	}
	
	

	def slackUserIdTagString = ""
	for(int i = 0; i< slackUserIDs.size(); i++)
	{
		def id = slackUserIDs[i]
		def addThis = true
		
		for(int j = 0; j< AddedSlackUserIDs.size(); j++)
		{
			if(id == AddedSlackUserIDs[j])
			{
				addThis = false
			}
		}
		
		if(addThis == true)
		{
			slackUserIdTagString = slackUserIdTagString + "<@" + "${id}" + """> """
			AddedSlackUserIDs.add(id)
		}
		
	
		
	}

	slackSend( channel: channelString,
		  color: '#CC3333',
		  message: "${slackUserIdTagString}",
		  tokenCredentialId: 'jenkinsAthensTantalus',
		  teamDomain: 'tantalus-group'
	)

}


def DoSlackReport(Map params, String successState)
{

	def submitter = findItemInChangelog("changeUser")
      def CLmsg = findItemInChangelog("msg")
	def channelString = 'notFound'

	if(params.containsKey("slackParams") == true)
	{
		def slackParam = params.slackParams
		if(slackParam.Channel != null)
		{
			channelString = slackParam.get("Channel")
		}
	}

	if(channelString == 'notFound')
	{
		channelString = '#athens-jenkins-notifications'
	}



	def stringOfAllCls = GenerateSlackMessageFromCls(params)

//Attempt to save log here:
// reference: http://mel1-bm-001:8080/job/${env.JOB_NAME}/lastBuild/console	
//	script{
//		"type D:/Jenkins/jobs/${JOB_NAME}/builds/${BUILD_NUMBER}/log > log.txt"
//		stash(name: 'stashOfLog', includes: '**/log.txt')  is this required along with an unstash? Seems weird.
//	}

/*
	def textLog = currentBuild.rawBuild.getLog(Integer.MAX_VALUE)

	writeFile(file: "log.txt", text: textLog[0], encoding: "UTF-8")
	
	def fileDataString = readFile("log.txt")

	for(int i = 1; i < textLog.size(); i++)
	{
		fileDataString += textLog[i] + "\n"
	}
	
	writeFile(file: "log.txt", text: fileDataString, encoding: "UTF-8")

*/


	if(successState == 'success')
	{
		slackSend( channel: channelString,
				color: 'good',
				message: "${currentBuild.fullDisplayName} completed successfully.",
				tokenCredentialId: 'jenkinsAthensTantalus',
				teamDomain: 'tantalus-group'
		)

	}
	else if(successState == 'regression')
	{

	slackSend( channel: channelString,
		  color: '#CC3333',
		  message: "+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+",
		  tokenCredentialId: 'jenkinsAthensTantalus',
		  teamDomain: 'tantalus-group'
	)

	slackSend( channel: channelString,
		  color: '#CC3333',
		  message: """${currentBuild.fullDisplayName} FAILED: One of the following changes has an error.
See log here:  <http://mel1-bm-001:8080/job/${env.JOB_NAME}/lastBuild/console|Console Link>
""" + stringOfAllCls,
		  tokenCredentialId: 'jenkinsAthensTantalus',
		  teamDomain: 'tantalus-group'
		)
	
	TagAppropriatePeople(channelString)	

	}
	else if(successState == 'fail')
	{

	slackSend( channel: channelString,
		  color: '#CC3333',
		  message: "+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+",
		  tokenCredentialId: 'jenkinsAthensTantalus',
		  teamDomain: 'tantalus-group'
	)

slackSend( channel: channelString,
		  color: '#CC3333',
		  message: """${currentBuild.fullDisplayName} FAILED: There was a fail in a previous build. The CL's in this build have been added, but MAY have errors un-caught.
See log here:  <http://mel1-bm-001:8080/job/${env.JOB_NAME}/lastBuild/console|Console Link>
""",
		  tokenCredentialId: 'jenkinsAthensTantalus',
		  teamDomain: 'tantalus-group'
		)

/*
	slackUploadFile(channel: channelString, 
							    filePath: "log.txt", 
							    credentialId: 'jenkinsAthensTantalus',
							    initialComment: "${JOB_NAME} ${BUILD_NUMBER}'s log:"
					)
*/

	TagAppropriatePeople(channelString)


	}
	else if(successState == 'fixed')
	{
		
		slackSend( channel: channelString,
			color: 'good',
			message: " Succeeded. Previous issue has been resolved.",
			tokenCredentialId: 'jenkinsAthensTantalus',
			teamDomain: 'tantalus-group'
		)
	}
}



def saveLogToTxtFile(Map params)
{	
	/*
	//File file = new File("${WORKSPACE}/${BUILD_NUMBER}-log.txt")
	File file = new File("chrissillyfileofsilly.txt")
	file.write "${JOB_NAME}"
	file << "\n This is a terrible test file to attach to slack. \n"
 
	println file.text
					
	if(build.workspace.isRemote())
	{
    		channel = build.workspace.channel;
    		fp = new FilePath(channel, build.workspace.toString() + "/chrissillyfileofsilly.txt")
	} else {

	*/

	filea = new File("${WORKSPACE}"+"/chrissillyfileofsilly.txt")
 	fp = new FilePath(filea)

println "FILE TEST 1 "

	def fileList = []
	def filesInDir = new File("${WORKSPACE}")
	getFileListFromDirectory(filesInDir,fileList)
	echo ">>> Found files: \n${fileList}"


	if(fp != null)
	{
	    fp.write("test data is here, to be tested.", null); //writing to file
	} 

	def testDataFromFile = readFile("${WORKSPACE}"+"/chrissillyfileofsilly.txt")

	println testDataFromFile

}



def call(Map params, String successState) {

	// SLACK PLUGIN EXPERIMENTATION
	//slackUserIdFromEmail(email: '" + EMAIL_ADDRESS + "', botUser: true)
	echo ">>> Cliff's email: 'ckang@forgottenempires.net'"
	
//	def testSlackUserId_NoBot = slackUserIdFromEmail("ckang@forgottenempires.net", false)
//	def testSlackUserId_YesBot = slackUserIdFromEmail("ckang@forgottenempires.net", true)

	def testSlackUserId_NoBot = slackUserIdFromEmail(email: "ckang@forgottenempires.net", botUser: false)	
	echo ">>> Cliff's user ID > NoBot: ${testSlackUserId_NoBot}"

	def testSlackUserId_YesBot = slackUserIdFromEmail(email: "ckang@forgottenempires.net", botUser: true)
	echo ">>> Cliff's user ID > YesBot: ${testSlackUserId_YesBot}"

	def userIds = slackUserIdsFromCommitters()
	echo ">>> slackUserIdsFromCommitters(): ${userIds}"
	def userIdsString = userIds.collect { "<@$it>" }.join(' ')
	echo ">>> slackUserIdsFromCommitters() [formatted into tags]: ${userIdsString}"
	// END EXPERIMENTATION

	if(params.slackParams != null)
	{
		def slackInfo = params.get("slackParams")
		if(slackInfo.containsKey("IncludeLogAsTxt") == true)
		{
			if(slackInfo.get("IncludeLogAsTxt") == true)
			{
				echo "log saved."
				saveLogToTxtFile(params)
			}
		}
	}


	
	if (params.runSlackReporting != null)
	{
		def runSlackReportings = params.get("runSlackReporting")
		if(runSlackReportings == true)
		{	
			echo ">>> Running DoSlackReport Func"
			DoSlackReport(params, successState);
			echo ">>> Slack report sent"
		}
	}

}

