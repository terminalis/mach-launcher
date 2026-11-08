// Mach Launcher site: focus model, keyboard map, deep links and wallpaper switch.
(function () {
  'use strict';

  var root = document.documentElement;
  var STORAGE_KEY = 'mach-site-wallpaper';
  var reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  var dock = document.querySelector('.dock');
  var tiles = Array.prototype.slice.call(document.querySelectorAll('.tile'));
  var heroItems = Array.prototype.slice.call(document.querySelectorAll('.hero-item'));
  var statusButtons = Array.prototype.slice.call(document.querySelectorAll('.status-btn'));
  var sections = tiles.map(function (tile) { return tile.dataset.section; });

  var selected = null;

  // ---------- Hero follows the selected tile ----------

  function tileFor(section) {
    return tiles[sections.indexOf(section)];
  }

  function select(section, options) {
    options = options || {};
    if (sections.indexOf(section) === -1) section = 'screenshots';
    if (section !== selected) {
      selected = section;
      tiles.forEach(function (tile) {
        if (tile.dataset.section === section) tile.setAttribute('aria-current', 'true');
        else tile.removeAttribute('aria-current');
      });
      heroItems.forEach(function (item) {
        item.classList.toggle('is-active', item.dataset.section === section);
      });
    }
    if (options.updateUrl && location.hash !== '#' + section) {
      history.replaceState(null, '', '#' + section);
    }
  }

  function showFocusLook(tile) {
    tiles.forEach(function (t) { t.classList.toggle('is-focused', t === tile); });
  }

  function sectionFromHash() {
    var hash = decodeURIComponent(location.hash.slice(1));
    return sections.indexOf(hash) === -1 ? null : hash;
  }

  // ---------- Focus and clicks ----------

  dock.addEventListener('focusin', function (event) {
    var tile = event.target.closest('.tile');
    if (!tile) return;
    showFocusLook(tile);
    select(tile.dataset.section, { updateUrl: true });
    tile.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: reducedMotion.matches ? 'auto' : 'smooth' });
  });

  dock.addEventListener('focusout', function (event) {
    if (!dock.contains(event.relatedTarget)) showFocusLook(null);
  });

  // Focus landing outside the dock also ends the on-load focus look.
  document.addEventListener('focusin', function (event) {
    if (!dock.contains(event.target)) showFocusLook(null);
  });

  tiles.forEach(function (tile) {
    tile.addEventListener('click', function (event) {
      // Safari does not focus links on click, so focus explicitly.
      if (document.activeElement !== tile) tile.focus({ preventScroll: true });
      select(tile.dataset.section, { updateUrl: true });
      if (tile.dataset.section !== 'github') event.preventDefault();
    });
  });

  document.addEventListener('focusin', function (event) {
    var card = event.target.closest && event.target.closest('.card');
    if (!card) return;
    card.scrollIntoView({ block: 'nearest', inline: 'center', behavior: reducedMotion.matches ? 'auto' : 'smooth' });
  });

  // ---------- Keyboard map ----------

  function visiblePills() {
    var item = heroItems.filter(function (i) { return i.dataset.section === selected; })[0];
    return item ? Array.prototype.slice.call(item.querySelectorAll('.hero-pill, .card')) : [];
  }

  function pressedStatusButton() {
    return statusButtons.filter(function (b) { return b.getAttribute('aria-pressed') === 'true'; })[0] || statusButtons[0];
  }

  function focusSelectedTile() {
    tileFor(selected).focus({ preventScroll: true });
  }

  function step(list, current, delta) {
    var index = list.indexOf(current) + delta;
    if (index >= 0 && index < list.length) list[index].focus({ preventScroll: true });
  }

  function press(element) {
    element.classList.add('is-pressed');
    setTimeout(function () { element.classList.remove('is-pressed'); }, 150);
  }

  document.addEventListener('keydown', function (event) {
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    var key = event.key;
    var target = event.target;
    var isArrow = key === 'ArrowLeft' || key === 'ArrowRight' || key === 'ArrowUp' || key === 'ArrowDown';

    var tile = target.closest && target.closest('.tile');
    var pill = target.closest && target.closest('.hero-pill, .card');
    var statusButton = target.closest && target.closest('.status-btn');

    // Nothing focused yet: the first arrow press focuses the selected tile.
    if (isArrow && (target === document.body || target === root)) {
      event.preventDefault();
      focusSelectedTile();
      return;
    }

    if (tile) {
      if (key === 'ArrowLeft' || key === 'ArrowRight') {
        event.preventDefault();
        step(tiles, tile, key === 'ArrowRight' ? 1 : -1);
      } else if (key === 'ArrowUp') {
        event.preventDefault();
        var pills = visiblePills();
        (pills[0] || pressedStatusButton()).focus({ preventScroll: true });
      } else if (key === 'ArrowDown') {
        event.preventDefault();
      } else if (key === ' ' || key === 'Enter') {
        if (key === ' ') {
          event.preventDefault();
          tile.click();
        }
        press(tile);
      }
      return;
    }

    if (pill) {
      if (key === 'ArrowLeft' || key === 'ArrowRight') {
        event.preventDefault();
        step(visiblePills(), pill, key === 'ArrowRight' ? 1 : -1);
      } else if (key === 'ArrowUp') {
        event.preventDefault();
        pressedStatusButton().focus({ preventScroll: true });
      } else if (key === 'ArrowDown') {
        event.preventDefault();
        focusSelectedTile();
      } else if (key === ' ') {
        event.preventDefault();
        pill.click();
      }
      return;
    }

    if (statusButton) {
      if (key === 'ArrowLeft' || key === 'ArrowRight') {
        event.preventDefault();
        step(statusButtons, statusButton, key === 'ArrowRight' ? 1 : -1);
      } else if (key === 'ArrowDown') {
        event.preventDefault();
        focusSelectedTile();
      } else if (key === 'ArrowUp') {
        event.preventDefault();
      }
    }
  });

  // ---------- Deep links ----------

  window.addEventListener('hashchange', function () {
    var section = sectionFromHash();
    if (section) select(section);
  });

  // ---------- Wallpaper switch ----------

  var walls = {
    day: document.querySelector('.wall-day'),
    night: document.querySelector('.wall-night'),
  };
  var switchToken = 0;

  function currentWallpaper() {
    return root.getAttribute('data-wallpaper') === 'night' ? 'night' : 'day';
  }

  function reflectWallpaper(name) {
    statusButtons.forEach(function (button) {
      button.setAttribute('aria-pressed', String(button.dataset.wallpaperChoice === name));
    });
  }

  function clearTransition() {
    Object.keys(walls).forEach(function (key) {
      walls[key].classList.remove('is-incoming', 'is-fading-in');
    });
  }

  function setWallpaper(name) {
    var token = ++switchToken;
    reflectWallpaper(name);
    clearTransition();
    if (name === currentWallpaper()) return;

    var incoming = walls[name];
    incoming.loading = 'eager';

    if (reducedMotion.matches) {
      root.setAttribute('data-wallpaper', name);
      return;
    }

    incoming.classList.add('is-incoming');
    var decoded = incoming.decode ? incoming.decode() : Promise.resolve();
    decoded.catch(function () {}).then(function () {
      if (token !== switchToken) return;
      requestAnimationFrame(function () {
        if (token !== switchToken) return;
        incoming.classList.add('is-fading-in');
        var finish = function () {
          if (token !== switchToken) return;
          root.setAttribute('data-wallpaper', name);
          clearTransition();
        };
        incoming.addEventListener('transitionend', finish, { once: true });
        setTimeout(finish, 1700);
      });
    });
  }

  function savedWallpaper() {
    try {
      var value = localStorage.getItem(STORAGE_KEY);
      return value === 'day' || value === 'night' ? value : null;
    } catch (e) {
      return null;
    }
  }

  statusButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      var name = button.dataset.wallpaperChoice;
      try { localStorage.setItem(STORAGE_KEY, name); } catch (e) {}
      setWallpaper(name);
    });
  });

  // Follow the system setting until the visitor makes a choice.
  var darkScheme = window.matchMedia('(prefers-color-scheme: dark)');
  var onSchemeChange = function (event) {
    if (!savedWallpaper()) setWallpaper(event.matches ? 'night' : 'day');
  };
  if (darkScheme.addEventListener) darkScheme.addEventListener('change', onSchemeChange);
  else if (darkScheme.addListener) darkScheme.addListener(onSchemeChange);

  // ---------- Start ----------

  reflectWallpaper(currentWallpaper());
  // Draw the selected tile in its focused state without moving DOM focus.
  select(sectionFromHash() || 'screenshots');
  if (!dock.contains(document.activeElement)) showFocusLook(tileFor(selected));
})();
