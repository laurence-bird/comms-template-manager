/*
        Basic validation for rendered print templates, injected by the template manager
        and executed on rendering of the PDF.

        Prince XML Output pixels for PDF: 96 dpi
        A4 page width in inches: 8.27 × 11.69 (inches)
        A4 page width in pixels: 793.82 x 1122.24 px
 */


const pageWidth = 790;
const pageHeight = 1120;

$(document).ready(function () {
    $("body").each(function (index, element) {
        checkHeight();
        loopChildren(element, checkElement);
    });
});

function checkWidth(element) {
    var width = $(element).width();
    if (width > pageWidth)
        throw "Element " + element.id + " is too wide to fit on an A4 page, (" + width + " pixels wide)";
}

function checkHeight(element) {
    var height = $(element).height();
    if (height > pageHeight)
        throw "Element " + element.id + " is too tall to fit on an A4 page, (" + height + " pixels tall)";
}

function checkElement(element) {
    checkWidth(element);
    if ($(element).css('break-inside') === 'avoid') {
        checkHeight();
    }
}

function loopChildren(element, checker) {
    const children = $(element).children();
    children.each(function (index, child) {
        checker(child);
        loopChildren(child, checker);
    });
}