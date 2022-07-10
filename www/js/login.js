/**
 * Created by cookeem on 16/6/3.
 */
app.controller('loginAppCtl', function($rootScope, $scope, $cookies, $timeout, $http) {
    //Hide sidebar when init
    $rootScope.showNavbar = false;
    //Hide footer when init
    $rootScope.showMessageArea = false;
    $timeout(function() {
        showHideSideBar($rootScope.showNavbar);
        $('label').addClass('active');
    }, 0);

    var cookie_login = "";
    var cookie_password = "";
    var cookie_remember = false;
    if ($cookies.get('login')) {
        cookie_login = $cookies.get('login');
    }
    if ($cookies.get('password')) {
        cookie_password = $cookies.get('password');
    }
    if ($cookies.get('remember')) {
        cookie_remember = true;
    }
    $scope.loginData = {
        "login": cookie_login,
        "password": cookie_password,
        "remember": cookie_remember
    };

    $scope.loginSubmit = function() {
        $scope.submitData = {
            "login": $scope.loginData.login,
            "password": $scope.loginData.password
        };

        $http({
            method  : 'POST',
            url     : '/api/loginUser',
            data    : $.param($scope.submitData),
            headers : { 'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8' }
        }).then(function successCallback(response) {
            console.log(response.data);
            if ($scope.loginData.remember) {
                $cookies.put('login', $scope.loginData.login);
                $cookies.put('password', $scope.loginData.password);
                $cookies.put('remember', $scope.loginData.remember);
            } else {
                $cookies.remove('login');
                $cookies.remove('password');
                $cookies.remove('remember');
            }
            if (response.data.errmsg) {
                $rootScope.errmsg = response.data.errmsg;
                Materialize.toast("error: " + $rootScope.errmsg, 4000);
            } else {
                //cookies will expires after 15 minutes
                var expiresDate = new Date();
                expiresDate.setTime(expiresDate.getTime() + 15 * 60 * 1000);
                $cookies.put('uid', response.data.uid, {'expires': expiresDate});
                $cookies.put('userToken', response.data.token, {'expires': expiresDate});

                $rootScope.successmsg = response.data.successmsg;
                Materialize.toast($rootScope.successmsg, 4000);
                window.location.href = '#!/chatlist';
            }
        }, function errorCallback(response) {
            console.info("error:" + response.data);
        });
    }
});


