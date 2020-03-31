$(document).ready(function() {
    var headerHeight = $('.wrapper-navbar').outerHeight();
    var homeheader = $(window).height() - headerHeight;
    $('.header-image').css('height', homeheader);
});
