
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Created by Paha on 4/6/2017.
 */
class Main {
    companion object {
        const val PIC_DIR_NAME = "pics"

        @JvmStatic fun main(args: Array<String>) {
            //Get the home dir and current executing path
            val homeDir = File(System.getProperty("user.home"))
            val currentPath = getExecutingPath()

            val picDir = File("$currentPath/$PIC_DIR_NAME")

            if(!picDir.exists()){
                if(picDir.mkdirs())
                    println("Made the dir")
            }else{
                println("Folder already existed at ${picDir.absolutePath}")
            }


            method2(homeDir, picDir)
        }

        fun method1(startDir:File, picDir:File){

            var startTime = System.currentTimeMillis()
            println("Starting search in $startDir")

            val list = getListOfFileNames(startDir)
            var endTime = (System.currentTimeMillis() - startTime)/1000

            println("Done searching in $startDir, took $endTime")

            startTime = System.currentTimeMillis()
            println("Copying ${list.size} files...")

            list.forEach { file ->
                file.copyTo(File("${picDir.path}/${file.name}"), true)
//                Files.copy(file, File("${picFile.path}/${file.name}"))
            }

            endTime = (System.currentTimeMillis() - startTime)/1000
            println("Done copying, took $endTime seconds")
        }

        fun getListOfFileNames(dir:File):MutableList<File>{
            val list = mutableListOf<File>()
            dirSearch(dir, list, true)
            return list
        }

        fun dirSearch(file:File, list:MutableList<File>, recursive:Boolean = false){
            file.listFiles().forEach { listedFile ->
//                println("Looking at ${listedFile.name}")
                val isDir = listedFile.isDirectory
                val fileList = listedFile.list()

                if(isDir && fileList != null && recursive)
                    dirSearch(listedFile, list, true)

                else if(!isDir) {
                    val filePath = listedFile.name
                    if(filePath.endsWith("png"))
                        list += listedFile
                }
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

        fun method2(startDir:File, picDir:File){

            var startTime = System.currentTimeMillis()
            println("Starting search in $startDir")

            var list = mutableListOf<Path>()

            Files.walkFileTree(startDir.toPath(), object:FileVisitor<Path>{
                override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {

                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if(!attrs.isDirectory && file.toString().endsWith("png")) {
                        list.add(file)
                    }

                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {

                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes?): FileVisitResult {

                    return FileVisitResult.CONTINUE
                }
            })

            var endTime = (System.currentTimeMillis() - startTime)/1000
            println("Done searching in $startDir, took $endTime seconds")

            startTime = System.currentTimeMillis()
            println("Starting copy of ${list.size} files...")

            val picDirString = picDir.path

            list.forEach { path ->
                val pathString = path.toString()
                val pathFileName = pathString.substringAfterLast('\\')
                val targetPath = Paths.get(picDirString, "/", pathFileName)

                try{
                    Files.copy(path, targetPath)
                }catch(e : AccessDeniedException){
                    println("Couldn't access file $pathString")
                }catch (e : FileAlreadyExistsException){
                    //Silently ignoring file already exists
                }
            }

            endTime = (System.currentTimeMillis() - startTime)/1000
            println("Done copying in $endTime seconds")

        }
    }
}