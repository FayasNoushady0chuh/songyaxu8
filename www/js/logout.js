/**
 * Created by cookeem on 16/6/2.
 */
app.controller('logoutAppCtl', function($rootScope, $scope, $cookies, $http, $timeout) {

    $rootScope.verifyUserToken();

    $scope.logoutSubmit = function() {
        var submitData = {
            "userToken": $rootScope.userToken
        };

        $http({
            method  : 'POST',
            url     : '/api/logoutUser',
            data    : $.param(submitData),
            headers : { 'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8' }
        }).then(function successCallback(response) {
            console.log(response.data);
            if (response.data.errmsg) {
                $rootScope.errmsg = response.data.errmsg;
                Materialize.toast("error: " + $rootScope.errmsg, 3000);
                window.history.back();
            } else {
                $cookies.remove('uid');
                $cookies.remove('userToken');
                $rootScope.uid = "";
                $rootScope.userToken = "";

                $rootScope.successmsg = response.data.successmsg;
                Materialize.toast($rootScope.successmsg, 3000);

                $rootScope.getUserTokenStop();

                window.location.href = '#!/login';
            }
        }, function errorCallback(response) {
            console.error("http request error:" + response.data);
        });
    };
    $scope.logoutSubmit();
});