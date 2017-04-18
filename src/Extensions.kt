/**
 * Created by Paha on 4/18/2017.
 */


/** The extension of the string (ie: png) without the period.*/
val String.extension : String
    get(){
        val index = this.lastIndexOf('.')
        if(index < 0) return ""
        return this.substring(index + 1)
    }