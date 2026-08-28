const button=document.querySelector('[data-menu-button]');
const navigation=document.querySelector('[data-site-nav]');
function closeMenu(){navigation?.classList.remove('open');button?.setAttribute('aria-expanded','false');button?.setAttribute('aria-label','Open navigation menu');}
button?.addEventListener('click',()=>{const open=!navigation.classList.contains('open');navigation.classList.toggle('open',open);button.setAttribute('aria-expanded',String(open));button.setAttribute('aria-label',open?'Close navigation menu':'Open navigation menu');});
navigation?.querySelectorAll('a').forEach(link=>link.addEventListener('click',closeMenu));
document.addEventListener('keydown',event=>{if(event.key==='Escape')closeMenu();});
document.addEventListener('click',event=>{if(navigation?.classList.contains('open')&&!navigation.contains(event.target)&&event.target!==button)closeMenu();});
