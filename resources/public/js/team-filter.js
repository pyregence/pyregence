function filterSelection(c) {
    var x, i;
    x = document.getElementsByClassName("team-block");
    if (c == "all") c = "";
    for (i = 0; i < x.length; i++) {
        if (x[i].className.indexOf(c) > -1) {
            w3AddClass(x[i], "show");
        } else {
            w3RemoveClass(x[i], "show");
        }
    }
}
function w3AddClass(element, name) {
    element.className = element.className.replace(name, "") + " " + name
}
function w3RemoveClass(element, name) {
    element.className = element.className.replace(name, "")
}
window.onload = function() {
    filterSelection("all")
    var btnContainer = document.getElementById("myBtnContainer");
    var btns = btnContainer.getElementsByClassName("btn");
    for (var i = 0; i < btns.length; i++) {
        btns[i].addEventListener("click", function(){
            var current = document.getElementsByClassName("active");
            current[0].className = current[0].className.replace(" active", "");
            this.className += " active";
        });
    }
}
