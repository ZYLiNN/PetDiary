// Reads the invitation from the part after "#" (never sent to the server) and hands it to the app.
(function () {
  var params = new URLSearchParams(location.hash.replace(/^#/, ''));
  var g = params.get('g') || '';
  var c = params.get('c') || '';
  var ok = /^[A-Za-z0-9-]{8,64}$/.test(g) && /^[a-z2-7]{20,64}$/.test(c);
  var open = document.getElementById('open');
  var copy = document.getElementById('copy');
  var msg = document.getElementById('msg');
  if (!ok) {
    open.style.display = 'none';
    copy.style.display = 'none';
    msg.className = 'bad';
    msg.textContent = '這個邀請連結不完整，請家人重新傳一次。';
    return;
  }
  var appLink = 'petdiary://join?g=' + g + '&c=' + c;
  open.href = 'intent://join?g=' + g + '&c=' + c + '#Intent;scheme=petdiary;package=com.zylinn.petdiary;end';
  copy.addEventListener('click', function () {
    var done = function () { msg.className = ''; msg.textContent = '已複製，打開 PetDiary → 設定 → 家庭群組 → 貼上連結。'; };
    if (navigator.clipboard) navigator.clipboard.writeText(appLink).then(done, function () { msg.textContent = appLink; });
    else msg.textContent = appLink;
  });
})();
