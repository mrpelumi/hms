(function () {
    const PAGINATION_HIDDEN = 'hms-pagination-hidden';
    const PAGINATED = 'data-hms-paginated';
    const renderingTables = new WeakSet();

    function rowsFor(table) {
        const body = table.tBodies && table.tBodies.length ? table.tBodies[0] : null;
        if (!body) {
            return [];
        }
        return Array.from(body.rows).filter(row => !row.dataset.paginationIgnore);
    }

    function inferPageSize(table) {
        const explicit = Number(table.dataset.pageSize || table.closest('[data-page-size]')?.dataset.pageSize);
        if (Number.isFinite(explicit) && explicit > 0) {
            return explicit;
        }
        const rowCount = rowsFor(table).length;
        const wrapper = table.closest('.overflow-auto, [class*="max-h-"], section, article');
        const wrapperClass = wrapper?.className || '';
        if (table.className.includes('text-caption') || wrapperClass.includes('max-h-56') || wrapperClass.includes('max-h-48') || wrapperClass.includes('max-h-[260px]')) {
            return 5;
        }
        if (wrapperClass.includes('max-h-[340px]') || wrapperClass.includes('max-h-[360px]')) {
            return 6;
        }
        if (rowCount > 40) {
            return 12;
        }
        if (rowCount > 20) {
            return 10;
        }
        return 8;
    }

    function visibleRows(table) {
        rowsFor(table).forEach(row => row.classList.remove(PAGINATION_HIDDEN));
        return rowsFor(table).filter(row => !row.classList.contains('hidden') && row.style.display !== 'none');
    }

    function ensureControls(table) {
        if (table.dataset.paginationControlId) {
            return document.getElementById(table.dataset.paginationControlId);
        }
        const id = 'table-pagination-' + Math.random().toString(36).slice(2);
        table.dataset.paginationControlId = id;
        const controls = document.createElement('div');
        controls.id = id;
        controls.className = 'hms-table-pagination flex flex-col gap-sm border-t border-border-base bg-white px-md py-sm text-caption text-text-secondary md:flex-row md:items-center md:justify-between';
        controls.innerHTML = `
            <p class="hms-pagination-summary">Showing records</p>
            <div class="flex flex-wrap items-center gap-xs">
                <button type="button" data-page-action="first" class="rounded-md border border-border-base px-sm py-xs font-semibold text-text-primary disabled:cursor-not-allowed disabled:opacity-40">First</button>
                <button type="button" data-page-action="prev" class="rounded-md border border-border-base px-sm py-xs font-semibold text-text-primary disabled:cursor-not-allowed disabled:opacity-40">Prev</button>
                <span class="hms-pagination-page px-xs font-semibold text-text-primary">1 / 1</span>
                <button type="button" data-page-action="next" class="rounded-md border border-border-base px-sm py-xs font-semibold text-text-primary disabled:cursor-not-allowed disabled:opacity-40">Next</button>
                <button type="button" data-page-action="last" class="rounded-md border border-border-base px-sm py-xs font-semibold text-text-primary disabled:cursor-not-allowed disabled:opacity-40">Last</button>
            </div>`;
        const wrapper = table.closest('.overflow-auto') || table.parentElement;
        wrapper.insertAdjacentElement('afterend', controls);
        controls.addEventListener('click', event => {
            const action = event.target.closest('[data-page-action]')?.dataset.pageAction;
            if (!action) {
                return;
            }
            const pageCount = Number(table.dataset.pageCount || 1);
            let page = Number(table.dataset.currentPage || 1);
            if (action === 'first') page = 1;
            if (action === 'prev') page -= 1;
            if (action === 'next') page += 1;
            if (action === 'last') page = pageCount;
            table.dataset.currentPage = String(Math.max(1, Math.min(page, pageCount)));
            renderTable(table);
        });
        return controls;
    }

    function renderTable(table) {
        renderingTables.add(table);
        const pageSize = inferPageSize(table);
        const rows = visibleRows(table);
        const pageCount = Math.max(1, Math.ceil(rows.length / pageSize));
        let page = Number(table.dataset.currentPage || 1);
        if (!Number.isFinite(page) || page < 1) {
            page = 1;
        }
        page = Math.min(page, pageCount);
        table.dataset.currentPage = String(page);
        table.dataset.pageCount = String(pageCount);
        rows.forEach((row, index) => {
            const onPage = index >= (page - 1) * pageSize && index < page * pageSize;
            row.classList.toggle(PAGINATION_HIDDEN, !onPage);
        });

        const controls = ensureControls(table);
        const start = rows.length ? ((page - 1) * pageSize) + 1 : 0;
        const end = Math.min(page * pageSize, rows.length);
        controls.querySelector('.hms-pagination-summary').textContent = `Showing ${start}-${end} of ${rows.length}`;
        controls.querySelector('.hms-pagination-page').textContent = `${page} / ${pageCount}`;
        controls.querySelector('[data-page-action="first"]').disabled = page <= 1;
        controls.querySelector('[data-page-action="prev"]').disabled = page <= 1;
        controls.querySelector('[data-page-action="next"]').disabled = page >= pageCount;
        controls.querySelector('[data-page-action="last"]').disabled = page >= pageCount;
        controls.classList.toggle('hidden', rows.length <= pageSize);
        window.requestAnimationFrame(() => renderingTables.delete(table));
    }

    function paginateTable(table) {
        if (table.getAttribute(PAGINATED) === 'true') {
            renderTable(table);
            return;
        }
        table.setAttribute(PAGINATED, 'true');
        table.dataset.currentPage = table.dataset.currentPage || '1';
        const body = table.tBodies && table.tBodies.length ? table.tBodies[0] : null;
        if (body) {
            const observer = new MutationObserver(mutations => {
                if (renderingTables.has(table)) {
                    return;
                }
                const externalVisibilityChanged = mutations.some(mutation => mutation.attributeName === 'class' || mutation.attributeName === 'style');
                if (externalVisibilityChanged) {
                    window.requestAnimationFrame(() => {
                        table.dataset.currentPage = '1';
                        renderTable(table);
                    });
                }
            });
            observer.observe(body, { attributes: true, subtree: true, attributeFilter: ['class', 'style'] });
        }
        renderTable(table);
    }

    function initPagination(root) {
        root.querySelectorAll('table').forEach(paginateTable);
    }

    if (!document.getElementById('hms-table-pagination-style')) {
        const style = document.createElement('style');
        style.id = 'hms-table-pagination-style';
        style.textContent = `.${PAGINATION_HIDDEN}{display:none!important;}`;
        document.head.appendChild(style);
    }

    document.addEventListener('DOMContentLoaded', () => initPagination(document));
    document.addEventListener('htmx:afterSwap', event => initPagination(event.target || document));
})();
