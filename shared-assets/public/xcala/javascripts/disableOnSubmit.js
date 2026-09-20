/**
 * Prevents duplicate form submissions and shows a loading indicator on the clicked button.
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
 * The clicked button keeps its exact size: its label is hidden with `visibility:hidden`
 * (not replaced by text) and a CSS "comet" ring is drawn over it in the button's text
 * colour. `window.disableOnSubmitWaitText` is no longer used.
 *
 * Code that submits with a native `form.submit()` (which fires no submit event) can call
 * `window.showButtonLoading(button)` itself — e.g. the reCAPTCHA callback in
 * `invisibleButtonExtended.scala.html`.
 *
 * Consumers load this with `@routes.Assets.at("xcala/javascripts/disableOnSubmit.js?v=N")`;
 * bump `N` in every include whenever this file changes, or browsers keep the old copy.
 */
(function ($) {

    const SUBMIT_SELECTOR = 'button[type=submit], input[type=submit]';

    // Injected rather than shipped as a stylesheet so every include site gets it with no extra <link>.
    // The comet is a conic-gradient (transparent tail → currentColor head) with its centre cut out
    // by a radial mask, rotated by an animation.
    if (!document.getElementById('btn-loading-style')) {
        $('<style id="btn-loading-style">').text(
            '.btn-loading{position:relative}' +
            '.btn-loading .btn-loading-text{visibility:hidden}' +
            '.btn-loading .btn-loading-spinner{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);' +
            'font-size:1.15em;pointer-events:none}' +
            '.btn-loading-spinner .btn-loading-comet{display:block;width:1.25em;height:1.25em;border-radius:50%;' +
            'background:conic-gradient(from 0deg,transparent 0 12%,currentColor);' +
            '-webkit-mask:radial-gradient(farthest-side,transparent calc(100% - .17em),#000 calc(100% - .16em));' +
            'mask:radial-gradient(farthest-side,transparent calc(100% - .17em),#000 calc(100% - .16em));' +
            'animation:btn-loading-spin .75s linear infinite}' +
            '@keyframes btn-loading-spin{to{transform:rotate(360deg)}}' +
            '@media (prefers-reduced-motion:reduce){.btn-loading-spinner .btn-loading-comet{animation-duration:2s}}' +
            'input.btn-loading{color:transparent!important}'
        ).appendTo('head');
    }

    // Marked so the bfcache reset below re-enables only what this script disabled.
    function disableButton($btn) {
        $btn.attr('data-loading-disabled', 'true').prop('disabled', true);
    }

    // Disables the button and swaps its label for the comet without changing its size:
    // the label is only hidden and the spinner is absolutely positioned.
    function showButtonLoading(el) {
        const $btn = $(el).first();

        if (!$btn.length || $btn.hasClass('btn-loading')) {
            return;
        }

        disableButton($btn);

        if ($btn.is('button')) {
            $btn.addClass('btn-loading')
                .attr('aria-busy', 'true')
                .wrapInner('<span class="btn-loading-text"></span>')
                .append('<span class="btn-loading-spinner" aria-hidden="true">' +
                    '<span class="btn-loading-comet"></span></span>');
        } else {
            // An <input> cannot hold children: lock its size and make its text transparent instead.
            $btn.css({width: $btn.outerWidth(), height: $btn.outerHeight()})
                .addClass('btn-loading')
                .attr('aria-busy', 'true');
        }
    }

    function resetButtonLoading(el) {
        const $btn = $(el);

        $btn.find('.btn-loading-spinner').remove();
        $btn.find('.btn-loading-text').contents().unwrap();

        if ($btn.is('input')) {
            $btn.css({width: '', height: ''});
        }

        $btn.removeClass('btn-loading').removeAttr('aria-busy');
    }

    window.showButtonLoading = showButtonLoading;

    // Guards against a second submit of any form on the page. Module-scoped rather than
    // per-form: a successful submit navigates away, so the flag dies with the page.
    let isSubmitting = false;

    $(function () {

        // Must be captured on click: by submit time the browser has already decided what the
        // payload contains, and `event.submitter` is not available in older browsers.
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

            const $submitter = $form.data('submitter');
            const name = $submitter && $submitter.attr('name');

            // Re-inject before disabling, otherwise this value is dropped from the POST body.
            if (name) {
                $('<input>')
                    .attr('type', 'hidden')
                    .attr('name', name)
                    .attr('data-submitter-proxy', 'true')
                    .val($submitter.val())
                    .appendTo($form);
            }

            disableButton($form.find(SUBMIT_SELECTOR));

            if ($submitter) {
                showButtonLoading($submitter);
            }

            return true;
        });

    });

    // Going Back (e.g. from a bank gateway) restores the page from the back/forward cache;
    // without this the buttons would stay disabled and spinning.
    window.addEventListener('pageshow', function (event) {

        if (!event.persisted) {
            return;
        }

        isSubmitting = false;

        $('.btn-loading').each(function () {
            resetButtonLoading(this);
        });

        $('[data-loading-disabled]').prop('disabled', false).removeAttr('data-loading-disabled');
        $('input[data-submitter-proxy]').remove();
    });

})(jQuery);
