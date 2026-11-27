package com.axora.app.data.repository

import java.io.IOException

// Ошибка с уже готовым текстом для пользователя: UI показывает message как есть,
// а повторные попытки (produceWithRetry) для неё отключены — повтор не поможет.
class UserFacingException(message: String) : IOException(message)
