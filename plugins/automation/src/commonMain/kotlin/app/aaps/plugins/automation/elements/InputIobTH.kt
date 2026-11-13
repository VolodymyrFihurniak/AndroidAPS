package app.aaps.plugins.automation.elements

/** autoISF: a value of the IOB threshold, in % of max IOB. */
class InputIobTH() {

    var value = 100

    constructor(another: Int) : this() {
        value = another
    }

    companion object {

        const val MIN = 10
        const val MAX = 100
    }
}
