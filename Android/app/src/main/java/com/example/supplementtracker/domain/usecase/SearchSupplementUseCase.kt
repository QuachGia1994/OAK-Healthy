package com.example.supplementtracker.domain.usecase

import android.content.Context
import com.example.supplementtracker.data.mock.SupplementDictionary
import com.example.supplementtracker.domain.model.Resource
import com.example.supplementtracker.domain.model.SupplementReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase tìm kiếm gợi ý thực phẩm bổ sung.
 */
class SearchSupplementUseCase(
    private val context: Context
) {
    
    /**
     * Tìm kiếm trong từ điển mock.
     */
    suspend operator fun invoke(query: String): Resource<List<SupplementReference>> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext Resource.Success(emptyList())
        
        try {
            val results = SupplementDictionary.localizedReferences(context).filter {
                it.name.contains(query, ignoreCase = true)
            }
            Resource.Success(results)
        } catch (e: Exception) {
            Resource.Error("Search failed: ${e.message}")
        }
    }
}
