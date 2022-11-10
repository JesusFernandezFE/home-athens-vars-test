@NonCPS
def call(item){
    def result = "Build ran manually"
    def found = false 
    def file = new XmlSlurper().parse("${env.HUDSON_CHANGELOG_FILE}")
    file.entry.each { entry ->
        entry.changenumber.each { changenumber ->
            changenumber.children().each { tag ->
                if(tag.name() == item && found != true){
                    result = tag.text()
                    found = true
                }
            }
        }
    }
    return result.toString()
}
