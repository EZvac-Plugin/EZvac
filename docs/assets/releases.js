// The front page's download cards: the latest release and the latest pilot, read
// live from GitHub's public releases list, each linking straight to its jar.
(function () {
  function show(id, release) {
    var card = document.getElementById(id);
    if (!card) return;
    if (!release) {
      card.style.display = "none";
      return;
    }
    var jar = (release.assets || []).find(function (a) { return /\.jar$/.test(a.name); });
    card.href = jar ? jar.browser_download_url : release.html_url;
    card.querySelector(".ez-tag").textContent = release.name || release.tag_name;
    var date = new Date(release.published_at);
    card.querySelector(".ez-date").textContent =
      (jar ? "Download the jar · " : "") + date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
  }

  function load() {
    if (!document.getElementById("ez-stable")) return;
    fetch("https://api.github.com/repos/EZvac-Plugin/EZvac/releases?per_page=30")
      .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function (all) {
        var published = all.filter(function (r) { return !r.draft; });
        published.sort(function (a, b) { return new Date(b.published_at) - new Date(a.published_at); });
        var stable = published.find(function (r) { return !r.prerelease; });
        var pilot = published.find(function (r) { return r.prerelease; });
        // A pilot older than the latest release isn't news.
        if (pilot && stable && new Date(pilot.published_at) < new Date(stable.published_at)) pilot = null;
        show("ez-stable", stable);
        show("ez-pilot", pilot);
      })
      .catch(function () {
        document.querySelectorAll(".ez-release .ez-tag").forEach(function (el) { el.textContent = "See all releases"; });
      });
  }

  // Material's instant navigation swaps pages without reloading: run on each one.
  if (window.document$) window.document$.subscribe(load);
  else document.addEventListener("DOMContentLoaded", load);
})();
