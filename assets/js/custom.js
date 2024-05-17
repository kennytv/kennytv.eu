$(document).ready(function () {
    $('#fullpage').fullpage({
        'verticalCentered': false,
        'scrollingSpeed': 600,
        'autoScrolling': false,
        'fitToSection': false,
        'css3': true,
        'navigation': false,
    });
});

// wow
$(function () {
    new WOW().init();
})