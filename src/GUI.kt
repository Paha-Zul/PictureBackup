
import events.ClosingEvent
import javafx.scene.Scene
import javafx.stage.Stage
import tornadofx.App
import tornadofx.UIComponent

/**
 * Created by Paha on 4/6/2017.
 */
class GUI : App(MyView::class){
    override fun start(stage: Stage) {
        super.start(stage)
        stage.width = 400.0
        stage.height = 200.0
    }

    override fun createPrimaryScene(view: UIComponent): Scene {
        return super.createPrimaryScene(view)
    }


    override fun stop() {
        fire(ClosingEvent)
        super.stop()
    }
}
