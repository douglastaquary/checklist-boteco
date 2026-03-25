package com.checklistboteco.platform

/**
 * Interface para captura de imagem da câmera.
 * Cada plataforma implementa de forma específica.
 */
interface ImageCapture {
    /**
     * Abre a câmera e retorna o caminho da imagem capturada.
     * Retorna null se o usuário cancelar ou houver erro.
     */
    suspend fun captureImage(): String?
}
