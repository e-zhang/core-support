
$(function() {
    initElem($(".partner"));

    $(".partner").dblclick(function() {
    	$(this).children("span").toggle();
        $(this).children("select").toggle();
        $(this).children("select").focus();
    }).focusout(function() {
    	$(this).children("span").toggle();
        $(this).children("select").toggle();
    });
   
    $("select").each(function(e) {
	$(this).data("previous", $(this).val());
	console.log($(this).val());
    });

    $("select").change(setSelect);
});


function setSelect() {
    console.log($(this).val());
    console.log($(this).data("previous"));
    console.log($(this).parent().siblings("#day").text());

    if($(this).data("previous") == $(this).val()) {
	console.log("same person!");
    }
    /*$.ajax({
	url: '/admin/schedule',
	type: 'POST',
	data: {
		'to' : $(this).val(),
		'to-date' : $(this).parent().siblings("#day").text(),
		'from-date' : ,
		'from' : $(this).data("previous")
	      },
	success: function(data, text, jqXHR){
	    console.log('success');
	}
    });*/
    $(this).blur();
};


function initElem(elem) {

    var options = {
        helper: "clone",
        revert: "invalid",
        cursor: "default"
    }; 

    elem.draggable(options);

    elem.droppable({
        accept: function(drag){
		return drag.hasClass("partner") &&
			drag.text() != $(this).text();
	},
        drop: function(ev, ui) {
            var drag = $(ui.draggable).clone();    
            var drop = $(this).clone();
            $(this).replaceWith(drag);
            $(ui.draggable).replaceWith(drop);
            initElem(drag);
            initElem(drop);
            console.log("DRAGGED   " + drag.siblings("#day").text() + "   " + drag.text());
            console.log("DROPPED   " + drop.siblings("#day").text() + "   " + drop.text());

            $.ajax({
                url: '/admin/schedule',
                type: 'POST',
                data: {
                        'from' : drag.text(),
                        'from-date' : drop.siblings("#day").text(),
                        'to' : drop.text(),
                        'to-date' : drag.siblings("#day").text()
                      },
		success: function(data, text, jqXHR){
		    console.log('success');
		}
            });
        }
    });
};


