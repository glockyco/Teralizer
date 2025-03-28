<?php
require_once('plugins/login-password-less.php');

return new AdminerLoginPasswordLess(
    $password_hash = password_hash("teralizer", PASSWORD_DEFAULT)
);
