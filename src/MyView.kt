
import events.ClosingEvent
import events.CopyDoneEvent
import events.ExistingFilesFoundEvent
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import tornadofx.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Created by Paha on 4/6/2017.
 */
class MyView : View() {
    override val root: VBox = VBox()
    val fileController : FileDataController by inject()

    private var task: Task <Unit>? = null
    private var uiTimer: Timer? = null

    private var currPath = ""

    private var progressBar : ProgressBar by singleAssign()
    private var fileNameLabel : Label by singleAssign()
    private var fileCountLabel : Label by singleAssign()
    private var startButton : Button by singleAssign()

    private var dirCheckBox : CheckBox by singleAssign()

    private var progressInfo:Triple<Path, Int, Int> = Triple(Paths.get(""), 0, 1)

    init {
        with(root){
            title = "Picture Backup v0.1"

            alignment = Pos.CENTER

            //The progress bar for our copying
            progressBar = progressbar {
                progress = 0.0
                setPrefSize(380.0, 40.0)
                vboxConstraints {
                    marginBottom = 20.0
                    hgrow = Priority.ALWAYS
                }
            }

            hbox{
                alignment = Pos.CENTER
                dirCheckBox = checkbox {
                    isSelected = true
                    fileController.copyDirs = true
                    action {
                        fileController.copyDirs = isSelected
                    }
                }

                label("Copy Directories"){
                }
            }

            //Where some information about the program goes
            fileNameLabel = label(""){
            }

            //Where some information about the program goes
            fileCountLabel = label(""){
            }

            //The start/stop button for the process
            startButton = button("Start") {
                //TODO Apparently can't restart once stopped? Broken stuff
                action {
                    val textValue = text
                    if (textValue == "Start") {
                        text = "Stop"

                        fileController.stopExecutionFlag = false

                        //Disable the checkbox so we can't change that
                        dirCheckBox.isDisable = true

                        task = runAsync {
                            fileController.getAllFilesInHome { currPath = it }
                        }.ui {
                            fileNameLabel.text = "Done"
                            uiTimer?.cancel()

                            //Run a task to copy all the files
                            task = runAsync {
                                fileController.copyAllFilesFoundInHome { info ->
                                    progressInfo = info
                                }
                            }

                            //Timer to update progress bar and stuff
                            uiTimer = Timer()
                            uiTimer!!.schedule(getUpdateProgressTask(), 0L, 50L)
                        }

                        //A timer to update the file name. This is done because updating a label in a background thread will
                        //crash the program. So instead we update a label with a timer...
                        uiTimer = Timer()
                        uiTimer!!.schedule(getUpdateFileNameTask(), 0L, 50L)
                    }else if(textValue == "Stop"){
                        text = "Start"

                        fileController.stopExecution() //Stop the controller execution
                        task?.cancel() //Cancel the background task
                        uiTimer?.cancel() //Cancel the timer

                        fileNameLabel.text = "Process Stopped... Waiting" //Set the status text
                    }
                }
            }
        }

        //We need to cancel the timer and the background task...
        subscribe<ClosingEvent> {
            uiTimer?.cancel()
            uiTimer = null

            task?.cancel()
        }

        subscribe<ExistingFilesFoundEvent> { evt -> fileCountLabel.text = "Existing files found: ${evt.size}" }

        subscribe<CopyDoneEvent> {
            startButton.text = "Start"
            fileCountLabel.text = "File Copying Done! Copied ${fileController.currCopyIndex}"
        }
    }

    fun getUpdateProgressTask() : TimerTask {
        return object:TimerTask(){
            override fun run() {
                Platform.runLater({
                    //Update UI here
                    fileNameLabel.text = "Copied ${progressInfo.second}/${progressInfo.third}"
                    progressBar.progress = progressInfo.second.toDouble()/progressInfo.third.toDouble()
//                    fileCountLabel.text = "Est: ${fileController.filesCopiedCounter/fileController.getTotalFiles()} seconds"
                    fileController.filesCopiedCounter = 0
                })
            }
        }
    }

    fun getUpdateFileNameTask() : TimerTask{
        //This takes a label and updates it with text. Thread safe!
        return object:TimerTask(){
            override fun run() {
                Platform.runLater({
                    //Update UI here
                    fileNameLabel.text = currPath
//                fileCountLabel.text = "Files Found: ${fileController.getTotalFiles()}"
                })
            }
        }
    }
}