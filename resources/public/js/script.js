$.fn.equalHeights = function(){ 
    var max_height = 0;
    $(this).each(function(){
        max_height = Math.max($(this).height(), max_height);
    });
    $(this).each(function(){
        $(this).height(max_height);
    });
};
$(document).ready(function() { 
    var headerHeight = $('.wrapper-navbar').outerHeight();
    var homeheader = $(window).height() - headerHeight;
    $('.header-image').css('height', homeheader);
    $(".icon").click(function () {
        $(".mobilenav").fadeToggle(500);
        $(".real-logo").fadeToggle(500);
        $(".yellow-logo").toggleClass("fade");
        $("body").toggleClass("mobile-open");
        $(".top-menu").toggleClass("top-animate");
        $(".mid-menu").toggleClass("mid-animate");
        $(".bottom-menu").toggleClass("bottom-animate");
    });
        $('.box-shadow').equalHeights();
});
