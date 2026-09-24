  function setAccess(on){
    authenticated=on;navigation.hidden=!on;root.querySelector('.admin-nav').hidden=!on;root.querySelector('.admin-access').hidden=!on;document.querySelector('.manage-label').hidden=!on;
    document.querySelector('.profile>div').innerHTML=on?esc(username)+'<small>Server administrator</small>':'Visitor<small>Public marketplace</small>';
    $('adminLoginLink').textContent=on?'Administration':'Admin sign in';
  }
  function showLogin(message=''){
    setAccess(false);
    if(website?.adminEnabled===false){currentContent().innerHTML='<div class="admin-empty">Web administration is disabled in the server configuration.</div>';return}
    const registering=!!registrationToken;
    currentContent().innerHTML=`<section class="admin-config-card" style="max-width:480px;margin:30px auto"><h2>${registering?'Create your admin account':'Admin sign in'}</h2><p>${registering?'Your in-game registration link authorizes this account. Choose a password.':'Use your DynamicShop web admin account. To register, an operator must run /shopadmin webadmin in game.'}</p><form id="liveLoginForm">${registering?'':input('loginUsername','Username','','required name="username" autocomplete="username"')}${input('loginPassword','Password','','type="password" required name="password" minlength="'+(registering?8:1)+'" autocomplete="'+(registering?'new-password':'current-password')+'"')}<button class="primary" type="submit">${registering?'Create account':'Sign in'}</button><p class="admin-form-error" id="loginError" role="alert">${esc(message)}</p></form></section>`;
    $('liveLoginForm').onsubmit=async event=>{event.preventDefault();if(authBusy)return;authBusy=true;const form=event.currentTarget,fields=new FormData(form),button=form.querySelector('button');const password=String(fields.get('password')||''),loginName=String(fields.get('username')||'').trim();button.disabled=true;try{
      if(!password||(!registering&&!loginName))throw new Error('Enter your username and password.');
      const result=await api('/api/auth/'+(registering?'register':'login'),{method:'POST',body:registering?{token:registrationToken,password}:{username:loginName,password}});
      adminSession=result.session;registrationToken='';try{localStorage.setItem('ds_admin_session',adminSession)}catch{}
      await signIn();
    }catch(error){if($('loginError'))$('loginError').textContent=error.message;else showLogin(error.message)}finally{authBusy=false;button.disabled=false}};
  }
  async function signIn(){
    const result=await api('/api/auth/verify',{auth:true});username=result.username||'Administrator';
    await loadAdmin();setAccess(true);renderAdmin();
  }
  const logout=document.createElement('button');logout.className='secondary';logout.textContent='Sign out';logout.hidden=true;root.querySelector('.admin-heading').append(logout);
  logout.onclick=async()=>{try{await api('/api/auth/logout',{auth:true,method:'POST',body:{}})}catch(error){notify(error.message);return}adminSession='';try{localStorage.removeItem('ds_admin_session')}catch{}logout.hidden=true;showLogin();notify('Signed out')};
  const originalSetAccess=setAccess;setAccess=on=>{originalSetAccess(on);logout.hidden=!on};
  window.addEventListener('admin-expired',()=>{adminSession='';try{localStorage.removeItem('ds_admin_session')}catch{}if(dialog.open)dialog.close();showLogin('Your session expired. Please sign in again.')});
  $('adminLoginLink').href='#settings';
  setAccess(false);
  if(website?.adminEnabled!==false&&adminSession){try{await signIn()}catch(error){showLogin(error.message)}}
  else if(registrationToken){
    try{const response=await fetch('/api/auth/verify',{headers:{'X-Admin-Token':registrationToken},cache:'no-store'});const data=await response.json();if(!response.ok||!data.valid){registrationToken='';showLogin('This registration link has expired. Run /shopadmin webadmin for a new link.')}else if(data.alreadyRegistered){registrationToken='';showLogin('Your admin account already exists. Sign in below.')}else showLogin()}
    catch(error){showLogin('Could not verify your registration link.')}
  }else showLogin();
  // Refresh shared appearance for visitors, but never replace an administrator's unsaved form.
  setInterval(async()=>{if(document.hidden||page==='settings')return;try{const next=await api('/api/website');if(validAppearance(next.appearance)){appearance=next.appearance;applyAppearance(appearance)}}catch{}},60000);
