// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { lazy, Suspense } from 'react'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { Empty, RequireAuth, Spinner } from './components/ui'
import { Home } from './pages/Home'
import { AppStateProvider } from './state/AppState'
import { PaymentProvider } from './state/Payments'

// Home loads with the first page; every other screen downloads only when it's opened.
const page = <K extends string>(load: () => Promise<Record<K, React.ComponentType>>, name: K) =>
  lazy(() => load().then((m) => ({ default: m[name] })))
const Shop = page(() => import('./pages/Shop'), 'Shop')
const Product = page(() => import('./pages/Product'), 'Product')
const Cart = page(() => import('./pages/Cart'), 'Cart')
const Wishlist = page(() => import('./pages/Wishlist'), 'Wishlist')
const Login = page(() => import('./pages/Login'), 'Login')
const Policy = page(() => import('./pages/Policy'), 'Policy')
const Checkout = page(() => import('./pages/Checkout'), 'Checkout')
const Pay = page(() => import('./pages/Pay'), 'Pay')
const OrderDetail = page(() => import('./pages/OrderDetail'), 'OrderDetail')
const Account = page(() => import('./pages/Account'), 'Account')
const Admin = page(() => import('./admin/Admin'), 'Admin')

export default function App() {
  return (
    <AppStateProvider>
      <PaymentProvider>
        <BrowserRouter>
          <Suspense fallback={<Spinner label="" />}>
            <Routes>
              <Route path="admin/*" element={<RequireAuth admin><Admin /></RequireAuth>} />
              <Route element={<Layout />}>
                <Route index element={<Home />} />
                <Route path="shop" element={<Shop />} />
                <Route path="p/:slug" element={<Product />} />
                <Route path="cart" element={<Cart />} />
                <Route path="wishlist" element={<Wishlist />} />
                <Route path="login" element={<Login />} />
                <Route path="policies/:slug" element={<Policy />} />
                <Route path="checkout" element={<RequireAuth><Checkout /></RequireAuth>} />
                <Route path="pay/:id" element={<RequireAuth><Pay /></RequireAuth>} />
                <Route path="account/orders/:id" element={<RequireAuth><OrderDetail /></RequireAuth>} />
                <Route path="account/*" element={<RequireAuth><Account /></RequireAuth>} />
                <Route path="*" element={<Empty title="Page not found"><Link to="/" className="underline">Go home</Link></Empty>} />
              </Route>
            </Routes>
          </Suspense>
        </BrowserRouter>
      </PaymentProvider>
    </AppStateProvider>
  )
}
