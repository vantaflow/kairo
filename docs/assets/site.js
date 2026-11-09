(() => {
  const nav = document.querySelector('nav'), burger = nav.querySelector('.burger'), drop = nav.querySelector('.drop'), dropBtn = drop.querySelector('button');
  burger.addEventListener('click', () => burger.setAttribute('aria-expanded', nav.classList.toggle('open')));
  dropBtn.addEventListener('click', () => dropBtn.setAttribute('aria-expanded', drop.classList.toggle('open')));
  nav.querySelectorAll('.links a').forEach(a => a.addEventListener('click', () => { nav.classList.remove('open'); drop.classList.remove('open'); burger.setAttribute('aria-expanded', 'false'); }));
  document.addEventListener('click', e => { if (!drop.contains(e.target)) drop.classList.remove('open'); });
})();
