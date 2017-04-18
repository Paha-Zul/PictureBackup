
import events.ClosingEvent
import events.CopyDoneEvent
import events.ExistingFilesFoundEvent
import events.ScanDoneEvent
import tornadofx.Controller
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Created by Paha on 4/7/2017.
 */
class FileDataController : Controller() {
    private val PIC_DIR_NAME = "pics"
    private val extensionSet = hashSetOf("png", "jpeg", "jpg")

    private var fileList = mutableListOf<FileData>()
    private var existingFileMap : HashMap<String, FileData> = hashMapOf()

    private lateinit var picDir:Path

    var currCopyIndex = 0
    var filesCopiedCounter = 0
    var copyDirs = false

    private var stopExecutionFlag = false

    private var threadPool : ThreadPoolExecutor? = ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, ArrayBlockingQueue(20))

    init{
        subscribe<ClosingEvent> {
            destroyThreads()
            stopExecutionFlag = true
        }
    }

    /**
     * Shuts down the thread pool and destroys it
     */
    private fun destroyThreads(){
        threadPool?.shutdownNow()
        threadPool = null
    }

    /**
     * Shuts down the thread pool and stops execution of tasks
     */
    fun stopExecution(){
        threadPool?.shutdownNow()
        stopExecutionFlag = true
    }

    fun getTotalFiles() : Int{
        return fileList.size
    }

    fun getAllFilesInHome(action: (String) -> Unit) {
        fileList = mutableListOf<FileData>() //Clear the list
        existingFileMap = hashMapOf() //Clear the map

        //Get the home dir and current executing path
        val homeDir = Paths.get(System.getProperty("user.home"))
        val currentPath = getExecutingPath()

        picDir = Paths.get("$currentPath/$PIC_DIR_NAME")

        //Create the picture backup directory if it doesn't exist
        if(!Files.exists(picDir)){
            Files.createDirectories(picDir) //Try to create the dir
            if(!Files.exists(picDir)) { //If the dir still doesn't exist, something went wrong
                println("Error, couldn't create picture directory")
                return
            }
            println("Made the dir")
        }else{
            println("Folder already existed at $picDir")
        }

        action.invoke("Scanning files from pic directory...")

        //Get all the files in the existing file map...
        val existingFiles = getAllFiles(picDir, hashMapOf()) //Search for existing files
        fire(ExistingFilesFoundEvent(existingFiles.size)) //Fire the event to tell the UI we have existing files

        //If we are copying the dirs, store by the relative file path... this will allow fine tune checking against duplicates
        if(copyDirs)
            existingFiles.forEach { existingFileMap.put(it.relativeFilePath.toString(), it) }

        //If we aren't copying the dir structure, we can store by the file name
        else
            existingFiles.forEach { existingFileMap.put(it.fileName, it) }

        fileList = getAllFiles(homeDir, existingFileMap, action)

        fire(ScanDoneEvent())
    }

    private fun getAllFiles(dirToSearch: Path, existingFileMap: java.util.HashMap<String, FileData>, action: ((String) -> Unit)? = null) : MutableList<FileData>{
        if(stopExecutionFlag)
            return mutableListOf()

        val fileList = mutableListOf<FileData>()

        //Walk the files....
        Files.walkFileTree(dirToSearch, object: FileVisitor<Path> {
            override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                action?.invoke("Found $file")
                val filePathString = file.toString() //Builds the string
                var filePathRelative = filePathString.replace(dirToSearch.toString(), "") //Get rid of the user path
                filePathRelative = filePathRelative.substring(1, filePathRelative.length) //Move 1 up to get rid of the front /
                val fileName = filePathString.substringAfterLast("\\") //Gets only the file name... someFile.png

                //If copying dirs, check relative path. Otherwise, check file name
                val existsAlready = if(copyDirs) existingFileMap.containsKey(filePathRelative) else existingFileMap.containsKey(fileName)

                //Make sure the file doesn't already exist in our existing file map...
                if(!existsAlready) {
                    val extension = fileName.extension.toLowerCase() //Get the extension of the name and lower case
                    if (!attrs.isDirectory && extensionSet.contains(extension)) { //If it's not a dir and matches our extensions
                        fileList.add(FileData(fileName, Paths.get(filePathRelative), file)) //Add the data to the list
                    }
                }

                if(stopExecutionFlag)
                    return FileVisitResult.TERMINATE

                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {

                return FileVisitResult.CONTINUE
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes?): FileVisitResult {

                return FileVisitResult.CONTINUE
            }
        })

        return fileList
    }

    fun copyAllFilesFoundInHome(action: (Triple<Path, Int, Int>) -> Unit){
        if(stopExecutionFlag)
            return

        copyFilesTo(this.fileList, picDir, existingFileMap, this.fileList.size, action)

        fire(CopyDoneEvent())
    }

    private fun copyFilesTo(fileList:MutableList<FileData>, destDir:Path, existingFileMap:java.util.HashMap<String, FileData>, numFiles:Int = 0,  action: (Triple<Path, Int, Int>) -> Unit){
        if(stopExecutionFlag)
            return

        val size = if(numFiles == 0) fileList.size else numFiles

        fileList.forEachIndexed{ index, (fileName, relativeFilePath, filePath) ->
            val relativeFilePathString = relativeFilePath.toString()
            val pathString = if(!copyDirs) relativeFilePathString.substringAfterLast('\\') else relativeFilePathString
            val targetPath = Paths.get(destDir.toString(), "/", pathString)

            //If we are copying the dirs, we need to check if they exist and create them
            if(copyDirs) {
                //TODO this might be slow. Maybe use a hashset to check paths we've already created vs Files.exists?
                val relativePathWithoutFile = relativeFilePathString.substringBeforeLast("\\")
//                val relativePathWithoutFilePath = Paths.get(relativePathWithoutFile)
                val destDirPathWithRelative = Paths.get(destDir.toString(), "\\", relativePathWithoutFile)
                if (!Files.exists(destDirPathWithRelative))
                    Files.createDirectories(destDirPathWithRelative)
            }

            if(!existingFileMap.containsKey(fileName)) {
                try {
                    if(stopExecutionFlag)
                        return

                    Files.copy(filePath, targetPath)

                    currCopyIndex++
                    filesCopiedCounter++
                    action.invoke(Triple(filePath, currCopyIndex, size))
                } catch(e: AccessDeniedException) {
                    println("Couldn't access file $pathString")
                } catch (e: FileAlreadyExistsException) {
                    //Silently ignoring file already exists
                }
            }
        }
    }

    fun copyAllFilesThreaded(action: (Triple<Path, Int, Int>) -> Unit){
        if(stopExecutionFlag)
            return

        val size = fileList.size
        val step = 8
        val incr = size/step

        val lists = mutableListOf<MutableList<FileData>>()

        for(i in 0..step-1){
            //If we are not at the end, split it up evenly
            if(i < (step-1))
                lists += fileList.subList(i*(size/incr), (i+1)*(size/incr - 1))

            //Otherwise, go to the end so we don't have leftovers
            else
                lists += fileList.subList(i*(size/incr), size-1)
        }

        lists.forEach{ list ->
            threadPool!!.submit{ copyFilesTo(list, picDir, existingFileMap, size, action) }
        }
    }

    fun getExecutingPath():String{
        var executingPath:String = ""

        try {
            executingPath = System.getProperty("user.dir")
            println("Executing at =>" + executingPath.replace("\\", "/"))
        } catch (e: Exception) {
            println("Exception caught =" + e.message)
        }
        return executingPath
    }
}
