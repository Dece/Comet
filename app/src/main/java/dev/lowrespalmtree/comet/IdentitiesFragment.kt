package dev.lowrespalmtree.comet

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.lowrespalmtree.comet.databinding.FragmentIdentitiesBinding

class IdentitiesFragment : Fragment() {
    private lateinit var binding: FragmentIdentitiesBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentIdentitiesBinding.inflate(layoutInflater)
        return binding.root
    }
}