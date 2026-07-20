/**
 * Prevents duplicate form submissions.
 *
 * The obvious implementation — disabling the submit buttons inside the submit handler —
 * is subtly wrong. The browser builds the form entry list *after* the submit event fires
 * and omits disabled controls, so the clicked button's name/value never reach the server.
 * Forms with several named submit buttons lose the discriminator that tells the server
 * which button was pressed.
 *
 * So the clicked button is recorded on click (before submit fires), its name/value are
 * re-injected as a hidden input, and only then are the buttons disabled.
 *
 * Multilingual projects set `window.disableOnSubmitWaitText` before loading this script;
 * single-language projects fall back to the Persian default.
 */
$(function () {

    const SUBMIT_SELECTOR = 'button[type=submit], input[type=submit]';

    // Guards against a second submit of any form on the page. Module-scoped rather than
    // per-form: a successful submit navigates away, so the flag dies with the page.
    let isSubmitting = false;

    function waitText() {
        return window.disableOnSubmitWaitText || 'لطفاً صبر کنید...';
    }

    function showBusy($submitter) {
        if ($submitter.is('button')) {
            $submitter
                .empty()
                .append($('<span>', { 'class': 'spinner-border spinner-border-sm me-1' }))
                .append(document.createTextNode(waitText()));
        } else {
            $submitter.val(waitText());
        }
    }

    // Fallback for browsers without `event.submitter` (see below): remember the last-clicked
    // submit button per form. This is a direct binding, so it can miss when another script
    // (e.g. a rich-text editor such as CKEditor) reworks the DOM after load — hence the
    // native `submitter` is preferred at submit time.
    $('form').find(SUBMIT_SELECTOR).on('click', function () {
        $(this).closest('form').data('submitter', $(this));
    });

    $('form').on('submit', function (event) {

        const $form = $(this);

        if (isSubmitting) {
            event.preventDefault();
            return false;
        }

        isSubmitting = true;

        // The browser sets `submitter` on the submit event for both mouse clicks and Enter-key
        // submits, and it stays correct even when a rich-text editor reworks the DOM after our
        // click handler was bound. Fall back to the click-captured button only when the native
        // property is unavailable (older browsers) or the submit was triggered programmatically.
        const nativeSubmitter = event.originalEvent && event.originalEvent.submitter;
        const $submitter = nativeSubmitter ? $(nativeSubmitter) : $form.data('submitter');
        const name = $submitter && $submitter.attr('name');

        // Re-inject before disabling, otherwise this value is dropped from the POST body.
        if (name) {
            $('<input>', {
                type : 'hidden',
                name : name,
                value: $submitter.val()
            }).appendTo($form);
        }

        $form.find(SUBMIT_SELECTOR).prop('disabled', true);

        if ($submitter) {
            showBusy($submitter);
        }

        return true;
    });

});
