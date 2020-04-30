$.fn.equalHeights = function () {
    var max_height = 0;
    $(this).each(function () {
        max_height = Math.max($(this).height(), max_height);
    });
    $(this).each(function () {
        $(this).height(max_height);
    });
};
$(document).ready(function () {
    var headerHeight = $('.wrapper-navbar').outerHeight();
    var homeheader = $(window).height() - headerHeight;
    $('.header-image').css('height', homeheader);
    $(".icon").click(function () {
        $(".mobilenav").fadeToggle(500);
        $(".real-logo").fadeToggle(500);
        $(".white-logo").toggleClass("fade");
        $("body").toggleClass("mobile-open");
        $(".top-menu").toggleClass("top-animate");
        $(".mid-menu").toggleClass("mid-animate");
        $(".bottom-menu").toggleClass("bottom-animate");
    });
});
//Header shrink on scroll
window.onscroll = function() {
    if (document.body.scrollTop > 50 || document.documentElement.scrollTop > 50) {
        document.getElementById("nav-row").style.padding = "0";
        document.getElementById("header").style.background = "#ffffffed";
    } else {
        document.getElementById("nav-row").style.padding = "1rem 0 1rem 0";
        document.getElementById("header").style.background = "#ffffff";
    }
};


